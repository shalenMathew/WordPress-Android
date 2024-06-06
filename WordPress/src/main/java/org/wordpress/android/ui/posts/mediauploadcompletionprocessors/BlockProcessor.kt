package org.wordpress.android.ui.posts.mediauploadcompletionprocessors

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.wordpress.android.editor.Utils
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.StringUtils
import org.wordpress.android.util.helpers.MediaFile

/**
 * Abstract class to be extended for each enumerated [MediaBlockType].
 */
abstract class BlockProcessor internal constructor(@JvmField var mLocalId: String, mediaFile: MediaFile) {
    @JvmField
    var mRemoteId: String = mediaFile.mediaId
    @JvmField
    var mRemoteUrl: String = StringUtils.notNullStr(
        Utils.escapeQuotes(
            mediaFile
                .optimalFileURL
        )
    )
    var mRemoteGuid: String? = mediaFile.videoPressGuid

    private var mBlockName: String? = null
    private var mJsonAttributes: JsonObject? = null
    private var mBlockContentDocument: Document? = null
    private var mClosingComment: String? = null

    private fun parseJson(blockJson: String) = JsonParser.parseString(blockJson).asJsonObject

    private fun parseHTML(blockContent: String): Document {
        // create document from block content
        val document = Jsoup.parse(blockContent)
        document.outputSettings(OUTPUT_SETTINGS)
        return document
    }

    private fun splitBlock(block: String, isSelfClosingTag: Boolean): Boolean {
        val captures =
            (if (isSelfClosingTag) MediaUploadCompletionProcessorPatterns.PATTERN_SELF_CLOSING_BLOCK_CAPTURES else MediaUploadCompletionProcessorPatterns.PATTERN_BLOCK_CAPTURES
                    ).matcher(block)

        val capturesGroup2 = captures.group(2)
        val capturesGroup3 = captures.group(3)
        val capturesFound = captures.find()


        if (capturesFound && capturesGroup2 != null && capturesGroup3 != null) {
            mBlockName = captures.group(1)
            mJsonAttributes = parseJson(capturesGroup2)
            mBlockContentDocument = if (isSelfClosingTag) null else parseHTML(capturesGroup3)
            mClosingComment = if (isSelfClosingTag) null else captures.group(4)
            return true
        } else {
            mBlockName = null
            mJsonAttributes = null
            mBlockContentDocument = null
            mClosingComment = null
            return false
        }
    }

    /**
     * Processes a block returning a raw content replacement string. If a match is not found for the block content, this
     * method should return the original block contents unchanged.
     *
     * @param block The raw block contents
     * @param isSelfClosingTag True if the block tag is self-closing (e.g. )
     * @return A string containing content with ids and urls replaced
     */
    @JvmOverloads
    fun processBlock(block: String, isSelfClosingTag: Boolean = false): String {
        if (splitBlock(block, isSelfClosingTag)) {
            if (processBlockJsonAttributes(mJsonAttributes)) {
                if (isSelfClosingTag) {
                    // return injected block
                    return StringBuilder()
                        .append("<!-- wp:")
                        .append(mBlockName)
                        .append(" ")
                        .append(mJsonAttributes) // json parser output
                        .append(" /-->")
                        .toString()
                } else if (processBlockContentDocument(mBlockContentDocument)) {
                    // return injected block
                    return StringBuilder()
                        .append("<!-- wp:")
                        .append(mBlockName)
                        .append(" ")
                        .append(mJsonAttributes) // json parser output
                        .append(" -->\n")
                        .append(mBlockContentDocument!!.body().html()) // HTML parser output
                        .append(mClosingComment)
                        .toString()
                }
            } else {
                return processInnerBlock(block) // delegate to inner blocks if needed
            }
        }
        // leave block unchanged
        return block
    }

    fun addIntPropertySafely(
        jsonAttributes: JsonObject, propertyName: String,
        value: String
    ) {
        try {
            jsonAttributes.addProperty(propertyName, value.toInt())
        } catch (e: NumberFormatException) {
            AppLog.e(AppLog.T.MEDIA, e.message)
        }
    }

    /**
     * All concrete implementations must implement this method for the particular block type. The document represents
     * the html contents of the block to be processed, and is to be mutated in place.<br></br>
     * <br></br>
     * This method should return true to indicate success. Returning false will result in the block contents being
     * unmodified.
     *
     * @param document The document to be mutated to make the necessary replacements
     * @return A boolean value indicating whether or not the block contents should be replaced
     */
    abstract fun processBlockContentDocument(document: Document?): Boolean

    /**
     * All concrete implementations must implement this method for the particular block type. The jsonAttributes object
     * is a [JsonObject] parsed from the block header attributes. This object can be used to check for a match,
     * and can be directly mutated if necessary.<br></br>
     * <br></br>
     * This method should return true to indicate success. Returning false will result in the block contents being
     * unmodified.
     *
     * @param jsonAttributes the attributes object used to check for a match with the local id, and mutated if necessary
     * @return
     */
    abstract fun processBlockJsonAttributes(jsonAttributes: JsonObject?): Boolean

    /**
     * This method can be optionally overriden by concrete implementations to delegate further processing via recursion
     * when [BlockProcessor.processBlockJsonAttributes] returns false (i.e. the block did not match
     * the local id being replaced). This is useful for implementing mutual recursion with
     * [MediaUploadCompletionProcessor.processContent] for block types that have media-containing blocks
     * within their inner content.<br></br>
     * <br></br>
     * The default implementation provided is a NOOP that leaves the content of the block unchanged.
     *
     * @param block The raw block contents
     * @return A string containing content with ids and urls replaced
     */
    open fun processInnerBlock(block: String): String {
        return block
    }

    companion object {
        /**
         * HTML output used by the parser
         */
        val OUTPUT_SETTINGS: Document.OutputSettings = Document.OutputSettings()
            .outline(false) //          .syntax(Syntax.xml)
            //            Do we want xml or html here (e.g. self closing tags, boolean attributes)?
            //            https://stackoverflow.com/questions/26584974/keeping-html-boolean-attributes-in-their-original-form-when-parsing-with-jsoup
            .prettyPrint(false)
    }
}
