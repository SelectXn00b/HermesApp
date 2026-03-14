/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package info.plateaukao.einkbro.browser

import info.plateaukao.einkbro.view.Album

interface AlbumController {
    val album: Album
    var albumTitle: String
    val albumUrl: String
    var initAlbumUrl: String

    var isTranslatePage: Boolean

    var isAIPage: Boolean

    fun activate()
    fun deactivate()

    fun pauseWebView()
    fun resumeWebView()
}