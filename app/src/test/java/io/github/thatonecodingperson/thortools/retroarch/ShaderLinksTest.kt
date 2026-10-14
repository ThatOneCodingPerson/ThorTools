package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShaderLinksTest {
    private fun repo(text: String) = ShaderLinks.parse(text) as ShaderLink.Repo

    @Test
    fun `GitHub links, to a repository, a folder, a file or a raw file`() {
        assertEquals(
            ShaderLink.Repo(GitHost.GITHUB, "libretro", "slang-shaders", null, ""),
            repo("https://github.com/libretro/slang-shaders"),
        )
        assertEquals(ShaderLink.Repo(GitHost.GITHUB, "libretro", "slang-shaders", null, ""), repo("github.com/libretro/slang-shaders.git/"))
        assertEquals(
            ShaderLink.Repo(GitHost.GITHUB, "libretro", "slang-shaders", "master", "handheld"),
            repo("https://github.com/libretro/slang-shaders/tree/master/handheld"),
        )
        assertEquals(
            ShaderLink.Repo(GitHost.GITHUB, "o", "r", "main", "crt/my crt.slangp"),
            repo("https://github.com/o/r/blob/main/crt/my%20crt.slangp?raw=true"),
        )
        assertEquals(
            ShaderLink.Repo(GitHost.GITHUB, "o", "r", "main", "a/b.slangp"),
            repo("https://raw.githubusercontent.com/o/r/main/a/b.slangp"),
        )
        assertEquals("github-libretro-slang-shaders", repo("https://github.com/libretro/slang-shaders").key)
        assertNull(ShaderLinks.parse("https://github.com/libretro"))
        assertNull(ShaderLinks.parse("https://github.com/o/r/issues/3"))
    }

    @Test
    fun `GitLab links with groups, Codeberg links, a direct preset, and what isn't one`() {
        assertEquals(ShaderLink.Repo(GitHost.GITLAB, "group/sub", "shaders", null, ""), repo("https://gitlab.com/group/sub/shaders"))
        assertEquals(
            ShaderLink.Repo(GitHost.GITLAB, "group", "shaders", "main", "crt"),
            repo("https://gitlab.com/group/shaders/-/tree/main/crt"),
        )
        assertEquals(
            ShaderLink.Repo(GitHost.GITLAB, "group", "shaders", "main", "a.slangp"),
            repo("https://gitlab.com/group/shaders/-/raw/main/a.slangp"),
        )
        assertEquals(ShaderLink.Repo(GitHost.CODEBERG, "o", "r", null, ""), repo("https://codeberg.org/o/r"))
        assertEquals(
            ShaderLink.Repo(GitHost.CODEBERG, "o", "r", "main", "crt/x.slangp"),
            repo("https://codeberg.org/o/r/src/branch/main/crt/x.slangp"),
        )
        val direct = ShaderLinks.parse("https://example.org/files/crt/my-crt.slangp") as ShaderLink.Direct
        assertEquals("files/crt/my-crt.slangp", direct.path)
        assertEquals("https://example.org/files/include/x.inc", ShaderLinks.directUrl(direct, "files/include/x.inc"))
        assertEquals(
            ShaderLink.Repo(GitHost.GITHUB, "libretro", "slang-shaders", "master", "crt/crt-lottes.slangp"),
            repo("https://cdn.jsdelivr.net/gh/libretro/slang-shaders@master/crt/crt-lottes.slangp"),
        )
        assertEquals(ShaderLink.Repo(GitHost.GITHUB, "o", "r", null, "a.slangp"), repo("https://cdn.jsdelivr.net/gh/o/r/a.slangp"))
        assertEquals(ShaderLink.Repo(GitHost.GITHUB, "o", "r", "main", "a.slangp"), repo("https://raw.githack.com/o/r/main/a.slangp"))
        val site = ShaderLinks.parse("https://shaders.example.org/gh/o/r@main/crt/a.slangp") as ShaderLink.Direct
        assertEquals(
            "https://shaders.example.org/gh/o/r@main/crt/shaders/a%20new.slang",
            ShaderLinks.directUrl(site, "gh/o/r@main/crt/shaders/a new.slang"),
        )
        assertNull(ShaderLinks.parse("http://example.org/a.slangp"))
        assertNull(ShaderLinks.parse("https://example.org/page"))
        assertNull(ShaderLinks.parse("not a link at all"))
    }

    @Test
    fun `each host's addresses`() {
        val github = ShaderLink.Repo(GitHost.GITHUB, "o", "r", null, "")
        assertEquals("https://api.github.com/repos/o/r", ShaderLinks.repoApi(github))
        assertEquals("https://api.github.com/repos/o/r/git/trees/main?recursive=1", ShaderLinks.treeApi(github, "main"))
        assertEquals(
            "https://raw.githubusercontent.com/o/r/main/crt/my%20crt.slangp",
            ShaderLinks.rawUrl(github, "main", "crt/my crt.slangp"),
        )
        val gitlab = ShaderLink.Repo(GitHost.GITLAB, "group/sub", "r", null, "")
        assertEquals("https://gitlab.com/api/v4/projects/group%2Fsub%2Fr", ShaderLinks.repoApi(gitlab))
        assertEquals("https://gitlab.com/group/sub/r/-/raw/main/a.slangp", ShaderLinks.rawUrl(gitlab, "main", "a.slangp"))
        val codeberg = ShaderLink.Repo(GitHost.CODEBERG, "o", "r", null, "")
        assertEquals(
            "https://codeberg.org/api/v1/repos/o/r/git/trees/main?recursive=true&per_page=100&page=1",
            ShaderLinks.treeApi(codeberg, "main"),
        )
        assertEquals("https://codeberg.org/o/r/raw/branch/main/a.slangp", ShaderLinks.rawUrl(codeberg, "main", "a.slangp"))
    }

    @Test
    fun `listings and their next pages`() {
        val github = """{"sha":"x","tree":[{"path":"crt","type":"tree"},{"path":"crt/a.slangp","type":"blob"}],"truncated":false}"""
        assertEquals(TreePage(listOf("crt/a.slangp"), null), ShaderLinks.parseTree(GitHost.GITHUB, github, null, "u"))
        val gitlab = """[{"id":"1","name":"a.slangp","type":"blob","path":"a.slangp","mode":"100644"}]"""
        val next = "https://gitlab.com/api/v4/projects/x/repository/tree?page_token=abc&per_page=100"
        val link = "<$next>; rel=\"next\", <https://x>; rel=\"first\""
        assertEquals(
            TreePage(listOf("a.slangp"), next),
            ShaderLinks.parseTree(GitHost.GITLAB, gitlab, link, "u"),
        )
        val codeberg = """{"tree":[{"path":"b.glslp","type":"blob"}],"truncated":true,"page":1,"total_count":150}"""
        assertEquals(
            TreePage(listOf("b.glslp"), "https://codeberg.org/api/v1/repos/o/r/git/trees/main?recursive=true&per_page=100&page=2"),
            ShaderLinks.parseTree(
                GitHost.CODEBERG,
                codeberg,
                null,
                "https://codeberg.org/api/v1/repos/o/r/git/trees/main?recursive=true&per_page=100&page=1",
            ),
        )
        val last = """{"tree":[{"path":"c.slangp","type":"blob"}],"truncated":true,"page":19,"total_count":1825}"""
        val lastUrl = "https://codeberg.org/api/v1/repos/o/r/git/trees/main?recursive=true&per_page=100&page=19"
        assertEquals(TreePage(listOf("c.slangp"), null), ShaderLinks.parseTree(GitHost.CODEBERG, last, null, lastUrl))
        val past = """{"sha":"x","tree":null,"truncated":false,"page":20,"total_count":1825}"""
        assertEquals(TreePage(emptyList(), null), ShaderLinks.parseTree(GitHost.CODEBERG, past, null, lastUrl.replace("=19", "=20")))
        assertEquals("main", ShaderLinks.defaultBranch("""{"default_branch":"main","id":3}"""))
        assertEquals(
            listOf("crt/a.slangp", "crt/b.glslp"),
            ShaderLinks.presets(listOf("crt/a.slangp", "crt/b.glslp", "crt/a.slang", "lcd/c.slangp"), "crt"),
        )
    }
}
