package com.amcentral365.service

import com.google.gson.JsonElement
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.Base64
import java.util.regex.Pattern

class SenderOfGitHub(url: String, translate: Boolean = true): TransferManager.Sender() {
    val topUrl: URL
    var curJsonElement: JsonElement? = null

    init {
        this.topUrl = translateUrlToApi(URL(url))
    }

    override fun begin() {
        curJsonElement = open(topUrl)
    }

    override fun getIterator(): Iterator<TransferManager.Item> {
        val url = this.topUrl

        return listOf(
            downloadFile(url)!!
        ).iterator()
    }

    private fun open(url: URL): JsonElement? {
        return gson.toJsonTree(null) // fixme
    }

    private fun downloadFile(url: URL): TransferManager.Item? {
        val jsonStr = url.readText()
        val map = gson.fromJson<Map<String, Any>>(jsonStr, Map::class.java)     // fixme: could be an array
        if( map.get("type").toString() != "file" )
            return null     // fixme: could be "dir"

        val filename = map.get("name").toString()
        val encoding = map.get("encoding")
        if( encoding != "base64")
            return null

        val content = map.get("content").toString().replace("\n", "")
        val filebytes = Base64.getDecoder().decode(content)

        return TransferManager.Item(pathStr = filename, inputStream = filebytes.inputStream())
    }

    /**
     * Translate Github-style URL (displayed in a browser) to the one, using GitHub API
     * from:
     *   https://github.com/am-central-365/scripts/blob/dev/am-central-365.com/test/single-file/create_earth.txt
     * to:
     *   https://api.github.com/repos/am-central-365/scripts/contents/am-central-365.com/test/single-file/create_earth.txt?ref=dev
     *
     * @param webUrl the regular URL as appears in the browser
     * @return URL, passable to GitHub API to fetch the file/directory contents
     */
    private fun translateUrlToApi(webUrl: URL): URL {
        if( webUrl.authority.isNullOrEmpty() )
            throw StatusException(400, "url has no host[:port] part: $webUrl")

        val API_HOSTNAME_PREFIX = "api."

        if( webUrl.authority.startsWith(API_HOSTNAME_PREFIX) && webUrl.path.startsWith("/repos"))
            return webUrl         // already an API url

        if( webUrl.authority.startsWith("raw.") )
            return webUrl         // a direct link to raw.githubusercontent.com

        val pattern = Pattern.compile("(.+)/(blob|tree)/(.+?)/(.+)")
        val matcher = pattern.matcher(webUrl.path)

        if( !matcher.matches() )
            throw StatusException(400, "url couldn't be parsed. Expected repo/(blob|tree)/branch/path, got: $webUrl")

        val repo = matcher.group(1)
      //val type = matcher.group(2)
        val branch = matcher.group(3)
        val path = matcher.group(4)

        var convertedPath = "/repos$repo/contents/$path?ref=$branch"
        if(!webUrl.query.isNullOrEmpty())
            convertedPath += "&${webUrl.query}"
        if(!webUrl.ref.isNullOrEmpty())
            convertedPath += "#${webUrl.ref}"

        return URL(webUrl.protocol, API_HOSTNAME_PREFIX+webUrl.authority, webUrl.port, convertedPath)
    }

}


/*  -H "Accept: application/json"
echo "content" | tr -d '\\n' | base64 -d


https://api.github.com/repos/am-central-365/scripts/contents/am-central-365.com/test/with-dirs/folder-a?ref=dev

$ curl 'https://api.github.com/repos/am-central-365/scripts/contents/README.md'
{
  "name": "README.md",
  "path": "README.md",
  "sha": "60576b94a53887dcc813d422147dba757dea2292",
  "size": 133,
  "url": "https://api.github.com/repos/am-central-365/scripts/contents/README.md?ref=master",
  "html_url": "https://github.com/am-central-365/scripts/blob/master/README.md",
  "git_url": "https://api.github.com/repos/am-central-365/scripts/git/blobs/60576b94a53887dcc813d422147dba757dea2292",
  "download_url": "https://raw.githubusercontent.com/am-central-365/scripts/master/README.md",
  "type": "file",
  "content": "U2NyaXB0cyByYW4gYnkgYW1DZW50cmFsMzY1Cj09PQoKIyMgQnJhbmNoZXMK\nKiBgZGV2YCAgICAtIHVzZWQgYnkgZGV2ZWxvcG1lbnQgZm9yIHRlc3Rpbmcg\nZG93bmxvYWQgZXRjCiogYG1hc3RlcmAgLSByZWxlYXNlZCBzY3JpcHRzCg==\n",
  "encoding": "base64",
  "_links": {
    "self": "https://api.github.com/repos/am-central-365/scripts/contents/README.md?ref=master",
    "git": "https://api.github.com/repos/am-central-365/scripts/git/blobs/60576b94a53887dcc813d422147dba757dea2292",
    "html": "https://github.com/am-central-365/scripts/blob/master/README.md"
  }
}

echo "..." | tr -d '\\n' | base64 -d

curl 'https://api.github.com/repos/am-central-365/scripts/contents/am-central-365.com/test/with-dirs/folder-a?ref=dev'

[
  {
    "name": "a-f1.txt",
    "path": "am-central-365.com/test/with-dirs/folder-a/a-f1.txt",
    "sha": "f967326ffdaef73ec959a7b614d3a1c1a7405467",
    "size": 30,
    "url": "https://api.github.com/repos/am-central-365/scripts/contents/am-central-365.com/test/with-dirs/folder-a/a-f1.txt?ref=dev",
    "html_url": "https://github.com/am-central-365/scripts/blob/dev/am-central-365.com/test/with-dirs/folder-a/a-f1.txt",
    "git_url": "https://api.github.com/repos/am-central-365/scripts/git/blobs/f967326ffdaef73ec959a7b614d3a1c1a7405467",
    "download_url": "https://raw.githubusercontent.com/am-central-365/scripts/dev/am-central-365.com/test/with-dirs/folder-a/a-f1.txt",
    "type": "file",
    "_links": {
      "self": "https://api.github.com/repos/am-central-365/scripts/contents/am-central-365.com/test/with-dirs/folder-a/a-f1.txt?ref=dev",
      "git": "https://api.github.com/repos/am-central-365/scripts/git/blobs/f967326ffdaef73ec959a7b614d3a1c1a7405467",
      "html": "https://github.com/am-central-365/scripts/blob/dev/am-central-365.com/test/with-dirs/folder-a/a-f1.txt"
    }
  },
  {
    "name": "a1",
    "path": "am-central-365.com/test/with-dirs/folder-a/a1",
    "sha": "b2bafe7f4cefb39b9c5eeb3a3bfdba5feca3ce98",
    "size": 0,
    "url": "https://api.github.com/repos/am-central-365/scripts/contents/am-central-365.com/test/with-dirs/folder-a/a1?ref=dev",
    "html_url": "https://github.com/am-central-365/scripts/tree/dev/am-central-365.com/test/with-dirs/folder-a/a1",
    "git_url": "https://api.github.com/repos/am-central-365/scripts/git/trees/b2bafe7f4cefb39b9c5eeb3a3bfdba5feca3ce98",
    "download_url": null,
    "type": "dir",
    "_links": {
      "self": "https://api.github.com/repos/am-central-365/scripts/contents/am-central-365.com/test/with-dirs/folder-a/a1?ref=dev",
      "git": "https://api.github.com/repos/am-central-365/scripts/git/trees/b2bafe7f4cefb39b9c5eeb3a3bfdba5feca3ce98",
      "html": "https://github.com/am-central-365/scripts/tree/dev/am-central-365.com/test/with-dirs/folder-a/a1"
    }
  }
]

 */
