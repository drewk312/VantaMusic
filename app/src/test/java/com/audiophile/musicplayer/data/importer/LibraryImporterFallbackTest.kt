package com.audiophile.musicplayer.data.importer

import com.audiophile.musicplayer.data.importer.ImportMatchStatus.NEEDS_REVIEW
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.testutil.InMemoryLibraryDao
import com.audiophile.musicplayer.testutil.newMetadataResolver
import com.audiophile.musicplayer.testutil.runSuspendTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryImporterFallbackTest {

    @Test
    fun importPastedText_marksMetadataOnlyFallbackAsNeedsReview() {
        val libraryDao = InMemoryLibraryDao()
        val repository = LocalLibraryRepository(libraryDao, newMetadataResolver(libraryDao))
        val importer = LibraryImporter(repository = repository)

        val batchId = runSuspendTest {
            importer.importPastedText(
                importName = "Ambiguous Import",
                pastedText = "Unmatched Song - Unknown Artist"
            )
        }

        val row = runSuspendTest { repository.importedTracksByBatchSnapshot(batchId).single() }

        assertNeedsReview(row)
    }

    @Test
    fun rerunMatching_keepsMetadataOnlyFallbackAsNeedsReview() {
        val libraryDao = InMemoryLibraryDao()
        val repository = LocalLibraryRepository(libraryDao, newMetadataResolver(libraryDao))
        val importer = LibraryImporter(repository = repository)

        val batchId = runSuspendTest {
            importer.importPastedText(
                importName = "Ambiguous Import",
                pastedText = "Unmatched Song - Unknown Artist"
            )
        }

        val updatedRows = runSuspendTest { importer.rerunMatchingForBatch(batchId) }

        assertNeedsReview(updatedRows.single())
    }

    private fun assertNeedsReview(row: ImportedTrackEntity) {
        assertEquals(NEEDS_REVIEW, row.matchStatus)
        assertEquals(PlayabilityStatus.NEEDS_REVIEW, row.playabilityStatus)
    }
}
