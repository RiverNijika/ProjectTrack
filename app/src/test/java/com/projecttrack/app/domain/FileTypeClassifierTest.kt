package com.projecttrack.app.domain

import com.projecttrack.app.data.file.FileTypeClassifier
import com.projecttrack.app.data.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class FileTypeClassifierTest {

    @Test
    fun jsonAndCommonCodeFilesAreClassifiedAsText() {
        val files = listOf(
            "data.json" to "application/json",
            "config.json" to "application/octet-stream",
            "Main.kt" to null,
            "App.java" to "text/plain",
            "script.py" to "application/octet-stream",
            "index.js" to "application/javascript",
            "types.ts" to "text/plain",
            "page.html" to "text/html",
            "style.css" to "text/css",
            "query.sql" to "application/octet-stream",
            "run.sh" to "application/x-sh",
            "README.md" to "text/markdown",
            "notes.log" to "text/plain",
            "settings.yaml" to "application/x-yaml",
        )
        files.forEach { (name, mime) ->
            assertEquals(
                "期望 $name 识别为文本",
                FileCategory.TEXT,
                FileTypeClassifier.classify(name, mime),
            )
        }
    }

    @Test
    fun audioAndVideoAreRecognizedByMimeOrExtension() {
        assertEquals(FileCategory.AUDIO, FileTypeClassifier.classify("song.mp3", null))
        assertEquals(FileCategory.AUDIO, FileTypeClassifier.classify("voice.ogg", "application/ogg"))
        assertEquals(FileCategory.AUDIO, FileTypeClassifier.classify("sound.bin", "audio/mpeg"))
        assertEquals(FileCategory.VIDEO, FileTypeClassifier.classify("clip.mp4", null))
        assertEquals(FileCategory.VIDEO, FileTypeClassifier.classify("movie.mkv", "video/x-matroska"))
        assertEquals(FileCategory.VIDEO, FileTypeClassifier.classify("blob.dat", "video/mp2t"))
    }

    @Test
    fun genericMimeFallsBackToExtension() {
        assertEquals(
            FileCategory.TEXT,
            FileTypeClassifier.classify("records.csv", "application/octet-stream"),
        )
        assertEquals(
            FileCategory.AUDIO,
            FileTypeClassifier.classify("track.flac", "binary/octet-stream"),
        )
    }

    @Test
    fun officeDocumentsAndImagesAreOther() {
        assertEquals(FileCategory.OTHER, FileTypeClassifier.classify("report.pdf", "application/pdf"))
        assertEquals(
            FileCategory.OTHER,
            FileTypeClassifier.classify(
                "slides.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ),
        )
        assertEquals(FileCategory.OTHER, FileTypeClassifier.classify("photo.png", "image/png"))
        assertEquals(FileCategory.OTHER, FileTypeClassifier.classify("archive.zip", "application/zip"))
    }

    @Test
    fun extensionNormalizationHandlesDotsAndCase() {
        assertEquals("mp3", FileTypeClassifier.extensionOf("SONG.MP3"))
        assertEquals("", FileTypeClassifier.extensionOf("no-extension"))
        assertEquals("", FileTypeClassifier.extensionOf(".gitignore"))
    }
}
