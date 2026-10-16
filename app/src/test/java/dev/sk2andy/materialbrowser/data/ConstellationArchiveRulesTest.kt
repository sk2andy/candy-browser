package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationArchiveRulesTest {
    @Test
    fun `page embeddings settings model weights and partial downloads stay device local`() {
        listOf(
            "no_backup/candy_constellations.db",
            "no_backup/candy_constellations.db-wal",
            "no_backup/candy_constellations.db-shm",
            "no_backup/candy_constellations.db-journal",
            "no_backup/constellation_model",
            "no_backup/constellation_model/model.onnx",
            "no_backup/constellation_model/model.onnx.part",
            "no_backup/constellation_model/vocab.txt",
        ).forEach { path -> assertFalse(path, AppDataArchiveRules.isAllowedArchiveRelativePath(path)) }
        assertTrue(AppDataArchiveRules.isAllowedArchiveRelativePath("no_backup/unrelated.json"))
    }
}
