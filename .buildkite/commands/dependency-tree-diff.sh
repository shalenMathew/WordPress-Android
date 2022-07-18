#!/bin/bash -eu

TARGET_BRANCH_DEPENDENCIES_FILE="target_branch_dependencies.txt"
CURRENT_TARGET_BRANCH_DEPENDENCIES_FILE="current_branch_dependencies.txt"
DIFF_DEPENDENCIES_FOLDER="./build/reports/diff"
DIFF_DEPENDENCIES_FILE="$DIFF_DEPENDENCIES_FOLDER/diff_dependencies.txt"
CONFIGURATION="wordpressVanillaReleaseRuntimeClasspath"
DEPENDENCY_TREE_VERSION="1.2.0"

REPO_HANDLE="wordpress-mobile/wordpress-android"

COMMIT_HASH=$BUILDKITE_COMMIT
PR_NUMBER=$BUILDKITE_PULL_REQUEST
PR_URL="https://api.github.com/repos/$REPO_HANDLE/pulls/$PR_NUMBER"
CURRENT_BRANCH=$BUILDKITE_BRANCH

echo "--> Starting the check"

git config --global user.email '$( git log --format='%ae' $COMMIT_HASH^! )'
git config --global user.name '$( git log --format='%an' $COMMIT_HASH^! )'

echo "--> Fetching the target branch from $PR_URL"
githubResponse="$(curl "$PR_URL" -H "Authorization: token $GITHUB_TOKEN")"
targetBranch=$(echo "$githubResponse" | tr '\r\n' ' ' | jq '.base.ref' | tr -d '"')
echo "--> Target branch is $targetBranch"

git merge "origin/$targetBranch" --no-edit

if [[ $(git diff --name-status "origin/$targetBranch" | grep ".gradle") ]]; then
    echo ".gradle files have been changed. Looking for caused dependency changes"
  else
    echo ".gradle files haven't been changed. There is no need to run the diff"
    ./gradlew dependencyTreeDiffCommentToGitHub -DGITHUB_PULLREQUESTID="${PR_NUMBER}" -DGITHUB_OAUTH2TOKEN="$GITHUB_TOKEN"
    exit 0
fi

mkdir -p "$DIFF_DEPENDENCIES_FOLDER"

echo "--> Generating dependencies to the file $CURRENT_TARGET_BRANCH_DEPENDENCIES_FILE"
./gradlew :WordPress:dependencies --configuration $CONFIGURATION > $CURRENT_TARGET_BRANCH_DEPENDENCIES_FILE

echo "--> Generating dependencies to the file $TARGET_BRANCH_DEPENDENCIES_FILE"
git checkout "$targetBranch"
./gradlew :WordPress:dependencies --configuration $CONFIGURATION > $TARGET_BRANCH_DEPENDENCIES_FILE

echo "--> Downloading dependency-tree-diff.jar"
# https://github.com/JakeWharton/dependency-tree-diff
curl -v -L "https://github.com/JakeWharton/dependency-tree-diff/releases/download/$DEPENDENCY_TREE_VERSION/dependency-tree-diff.jar" -o dependency-tree-diff.jar
sha=($(sha1sum dependency-tree-diff.jar))
if [[ $sha != "949394274f37c06ac695b5d49860513e4d16e847" ]]; then
  echo "dependency-tree-diff.jar file has unexpected sha1"
  exit 1
fi
chmod +x dependency-tree-diff.jar

echo "--> Running dependency-tree-diff.jar"
./dependency-tree-diff.jar $TARGET_BRANCH_DEPENDENCIES_FILE $CURRENT_TARGET_BRANCH_DEPENDENCIES_FILE > $DIFF_DEPENDENCIES_FILE

git checkout "$CURRENT_BRANCH"
if [ -s $DIFF_DEPENDENCIES_FILE ]; then
  echo "There are changes in dependencies of the project"
  cat "$DIFF_DEPENDENCIES_FILE"
else
  echo "There are no changes in dependencies of the project"
  rm "$DIFF_DEPENDENCIES_FILE"
fi

echo "--> Commenting result to GitHub"
./gradlew dependencyTreeDiffCommentToGitHub -DGITHUB_PULLREQUESTID="${CIRCLE_PULL_REQUEST##*/}" -DGITHUB_OAUTH2TOKEN="$GITHUB_API_TOKEN" --info
