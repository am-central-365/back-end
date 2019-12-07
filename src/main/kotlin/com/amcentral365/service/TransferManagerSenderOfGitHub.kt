package com.amcentral365.service

import java.net.URL
import java.util.regex.Pattern

class TransferManagerSenderOfGitHub(url: String): TransferManager.Sender() {
    val GITHUB_API_PREFIX = "/api/v3/"

    val url: URL;

    init {
        this.url = translateUrlToApi(URL(url))
    }

    override fun begin() {}
    override fun end(successful: Boolean) {}

    override fun getIterator(): Iterator<TransferManager.Item> {
        return emptyList<TransferManager.Item>().iterator()  // FIXME
    }

    /**
     * Translate Github-style URL (i.e displayed in a browser) to one, recognized by GitHub API
     * from:
     *   https://github.com/DBTools/c3scripts/blob/master/mysql/cloudrdbms/test.sh
     * to:
     *   https://github.com/api/v3/repos/DBTools/c3scripts/contents/mysql/cloudrdbms/test.sh?ref=master
     *
     * @param githubURL the regular URL as appears in browser
     * @return URL, passable to GitHub API to fetch the file/directory contents
     */
    private fun translateUrlToApi(webUrl: URL): URL {
        if( webUrl.authority.isNullOrEmpty() )
            throw StatusException(400, "url has no host[:port] part: $webUrl")
        if( webUrl.path.isNullOrEmpty() )
            throw StatusException(400, "url has no path: $webUrl")

        if( webUrl.path.startsWith(GITHUB_API_PREFIX))
            return webUrl         // already an API url

        val pattern = Pattern.compile("(.+)/(blob|tree)/(.+?)/(.+)")
        val matcher = pattern.matcher(webUrl.path)

        if( !matcher.matches() )
            throw StatusException(400, "url couldn't be parsed. Expected repo/(blob|tree)/branch/path, got: $webUrl")

        val repo = matcher.group(1)     //
      //val type = matcher.group(2)
        val branch = matcher.group(3)
        val path = matcher.group(4)

        var convertedPath = "${GITHUB_API_PREFIX}repos$repo/contents/$path?ref=$branch"
        if( !webUrl.query.isNullOrEmpty() )
            convertedPath += "&${webUrl.query}"
        if( !webUrl.ref.isNullOrEmpty() )
            convertedPath += "#${webUrl.ref}"

        return URL(webUrl.protocol, webUrl.authority, webUrl.port, convertedPath)
    }

}
