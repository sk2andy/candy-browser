package dev.sk2andy.materialbrowser.reader

internal data class PocketSpeechArtifact(val name: String, val size: Long, val sha256: String)

internal data class PocketSpeechLanguage(
    val code: String,
    val languageTag: String,
    val voiceId: String,
    val archiveName: String,
    val downloadBytes: Long,
    val sha256: String,
    val files: List<PocketSpeechArtifact>,
    val url: String = "${PocketSpeechCatalog.BASE_URL}/$archiveName",
) {
    val installedBytes: Long get() = files.sumOf(PocketSpeechArtifact::size)
}

/** FP32 packs from the pinned v0.5.4 release; archive and extracted files have independent hashes. */
internal object PocketSpeechCatalog {
    const val BASE_URL = "https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/" +
        "releases/download/v0.5.4"

    val languages: List<PocketSpeechLanguage> = listOf(
        PocketSpeechLanguage(
            code = "de",
            languageTag = "de-DE",
            voiceId = "juergen",
            archiveName = "PocketTTS-german-FP32.zip",
            downloadBytes = 207_126_318L,
            sha256 = "e45e5d0b70dadc9ab9f86f30ae519d79dbfd8adf013d42826af9205545cfdcb3",
            files = listOf(
                PocketSpeechArtifact(
                    "MODEL_LICENSE.txt", 1_810L,
                    "590118cb876598e06aa772e94d1dbf8e1bd712bc0dd81eac7fabcf9759ebcbbe",
                ),
                PocketSpeechArtifact(
                    "VOICE_ATTRIBUTION.md", 2_001L,
                    "a0b0bdc0c2d5d0736b8fe15ec5ba4cb6c107e131eb00c1d0e3dbf1026456bca8",
                ),
                PocketSpeechArtifact(
                    "manifest.json", 336L,
                    "c4c5fbcde03c474933b5380267ed178ccb7e5758afab3bf78286493f5f4c3893",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_flow.onnx", 39_081_891L,
                    "b6cad06ac9fea67363095f591c6f5327d6329e2ccd70687259da482d4625dc11",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_main.onnx", 302_364_168L,
                    "f40ac589ceec3fda9bed9637b80db5acaa4ba4561bd1e79580e9942af1b7450f",
                ),
                PocketSpeechArtifact(
                    "models/mimi_decoder.onnx", 41_412_445L,
                    "e1b87a481b43812f089e8ab69c928310e9dce8fdae7b74b09dc6192f8426b39a",
                ),
                PocketSpeechArtifact(
                    "models/mimi_encoder.onnx", 39_283_560L,
                    "7c0874b0b45d5e498793dca0b6e1a89e104ef26a26fdbd7e15ffeec9c90778d2",
                ),
                PocketSpeechArtifact(
                    "models/text_conditioner.onnx", 16_388_310L,
                    "e30262a0a2d49e10dfe273e7a1271c8cbf27e26fc95fa9ada0e768a4490ae369",
                ),
                PocketSpeechArtifact(
                    "models/tokenizer.model", 59_837L,
                    "389079b9c67cd17ff1c565d53bc695df390b4ca8abf2300947e659585edd9d2a",
                ),
                PocketSpeechArtifact(
                    "voices/juergen.wav", 480_510L,
                    "b986fc14334956ec8785ce3c86df508794c999b6871f633f45aec57f8261036a",
                ),
            ),
        ),
        PocketSpeechLanguage(
            code = "en",
            languageTag = "en-US",
            voiceId = "alba",
            archiveName = "PocketTTS-english-FP32.zip",
            downloadBytes = 207_764_771L,
            sha256 = "f2027032434a8616855c12f505fec8864bcbaccd42de43bdf69bff340fb411c4",
            files = listOf(
                PocketSpeechArtifact(
                    "MODEL_LICENSE.txt", 1_810L,
                    "590118cb876598e06aa772e94d1dbf8e1bd712bc0dd81eac7fabcf9759ebcbbe",
                ),
                PocketSpeechArtifact(
                    "VOICE_ATTRIBUTION.md", 2_001L,
                    "a0b0bdc0c2d5d0736b8fe15ec5ba4cb6c107e131eb00c1d0e3dbf1026456bca8",
                ),
                PocketSpeechArtifact(
                    "manifest.json", 328L,
                    "2a831830d4aeecf72f59c604b865572fcd6f76deff125ae1d8fb88764e2c0c2e",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_flow.onnx", 39_081_891L,
                    "cd2a6e2704621088778f4b6e969736a7117a2b75e6127349281b84d8969df36f",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_main.onnx", 302_364_168L,
                    "2181b8d2b3f0633d8cadda9637a1beba45013f9ed6bf78ee45ec3216be1eb329",
                ),
                PocketSpeechArtifact(
                    "models/mimi_decoder.onnx", 41_412_445L,
                    "25f979e6cca20742ded65edb7b963fba705c36ce420229df7edf3dc52c397bf0",
                ),
                PocketSpeechArtifact(
                    "models/mimi_encoder.onnx", 39_283_560L,
                    "59ff3c6f5134792808a9d0480b0b64370be01893b7aa3cc01b89802f128248e3",
                ),
                PocketSpeechArtifact(
                    "models/text_conditioner.onnx", 16_388_310L,
                    "6d66994acc358324667e22c56358125acd3e6d219f013e110dbc7b542595c3a2",
                ),
                PocketSpeechArtifact(
                    "models/tokenizer.model", 59_339L,
                    "d461765ae179566678c93091c5fa6f2984c31bbe990bf1aa62d92c64d91bc3f6",
                ),
                PocketSpeechArtifact(
                    "voices/alba.wav", 479_310L,
                    "ea726819f69bcb9bb7398fc2937645002c22c6a235b93d94da63d28434012c7e",
                ),
            ),
        ),
        PocketSpeechLanguage(
            code = "es",
            languageTag = "es-ES",
            voiceId = "lola",
            archiveName = "PocketTTS-spanish-FP32.zip",
            downloadBytes = 207_487_086L,
            sha256 = "f83dc41bd0d5c7634d385fdadef496881d398ca99d1886ff25d90cb59e384412",
            files = listOf(
                PocketSpeechArtifact(
                    "MODEL_LICENSE.txt", 1_810L,
                    "590118cb876598e06aa772e94d1dbf8e1bd712bc0dd81eac7fabcf9759ebcbbe",
                ),
                PocketSpeechArtifact(
                    "VOICE_ATTRIBUTION.md", 2_001L,
                    "a0b0bdc0c2d5d0736b8fe15ec5ba4cb6c107e131eb00c1d0e3dbf1026456bca8",
                ),
                PocketSpeechArtifact(
                    "manifest.json", 329L,
                    "a1f9124772df0adc322178e3a0b563e79269dcaa1f373cde45529a01940c7368",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_flow.onnx", 39_081_891L,
                    "18cd2509bb95e006efc2f8ad895c066bb922ab22072f724426b5a635c5714688",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_main.onnx", 302_364_168L,
                    "3a99e5e30d108f3cf320f24f71eb6e9ce37bff1a2d88b7dc937272b71bbc5bd1",
                ),
                PocketSpeechArtifact(
                    "models/mimi_decoder.onnx", 41_412_445L,
                    "78bf9bd7ee8f0c5f0bfa3960eee25b2cc95ac2668d73b30057acc8ce93a60fd5",
                ),
                PocketSpeechArtifact(
                    "models/mimi_encoder.onnx", 39_283_560L,
                    "e693d3c16e40395948279716adf5390722d47795fb2f9adfec5f57aac3b7173e",
                ),
                PocketSpeechArtifact(
                    "models/text_conditioner.onnx", 16_388_310L,
                    "9d25213de2a973a76e776b41f8a053f7f1cc4fcc84682a7154f67fc3711db245",
                ),
                PocketSpeechArtifact(
                    "models/tokenizer.model", 60_895L,
                    "aac2b96478e3e3068712bd5c45dc196f84b7341ea72587c33a2094957f0c1b0c",
                ),
                PocketSpeechArtifact(
                    "voices/lola.wav", 458_622L,
                    "71e8ab70b44e7a6699336a71b5665f8b9a2d6b2f9f76a860ea1ad2345091a982",
                ),
            ),
        ),
        PocketSpeechLanguage(
            code = "it",
            languageTag = "it-IT",
            voiceId = "giovanni",
            archiveName = "PocketTTS-italian-FP32.zip",
            downloadBytes = 207_287_262L,
            sha256 = "50ccb4c0fe3f2fdcd00a86b9f31112c8c3bef92cc0fe528c761270e4f8896a8d",
            files = listOf(
                PocketSpeechArtifact(
                    "MODEL_LICENSE.txt", 1_810L,
                    "590118cb876598e06aa772e94d1dbf8e1bd712bc0dd81eac7fabcf9759ebcbbe",
                ),
                PocketSpeechArtifact(
                    "VOICE_ATTRIBUTION.md", 2_001L,
                    "a0b0bdc0c2d5d0736b8fe15ec5ba4cb6c107e131eb00c1d0e3dbf1026456bca8",
                ),
                PocketSpeechArtifact(
                    "manifest.json", 341L,
                    "fa3d6ad1a09cd29b5dff186fbbd4f9cd748314f77987aebaefc239b5cdb8e09e",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_flow.onnx", 39_081_891L,
                    "45c312dc908efc57f634bff4377aff2cae6efed6b737bdb7ee01ecadbe557933",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_main.onnx", 302_364_168L,
                    "afde3c2c99e3b43453af6f5f9e6f5401634df4b9a1eb5a437dc6fc6a6683fda7",
                ),
                PocketSpeechArtifact(
                    "models/mimi_decoder.onnx", 41_412_445L,
                    "d53b5ffa99e065d67e6ece34cd4b81c86e98f4ee468d6c24faf20fe43ea68d56",
                ),
                PocketSpeechArtifact(
                    "models/mimi_encoder.onnx", 39_283_560L,
                    "e11fef8ece422990b9fcf22fb4d92a9661c45130b49ddef272791559ba608d29",
                ),
                PocketSpeechArtifact(
                    "models/text_conditioner.onnx", 16_388_310L,
                    "3be21ae656ef084e1e6006ef175bd365dbc5b4d1eda1e70059fd0aa11c016509",
                ),
                PocketSpeechArtifact(
                    "models/tokenizer.model", 60_078L,
                    "6583b974a11b90e14d8a4c8e9c43f06c3861b9ede6e5023a4c27ab5a3a7d4c39",
                ),
                PocketSpeechArtifact(
                    "voices/giovanni.wav", 354_318L,
                    "b6d36df96ac8d520523fdccf6562bb90d026d5e3aa54f5c2203c1548b4f27748",
                ),
            ),
        ),
        PocketSpeechLanguage(
            code = "pt",
            languageTag = "pt-PT",
            voiceId = "rafael",
            archiveName = "PocketTTS-portuguese-FP32.zip",
            downloadBytes = 207_503_964L,
            sha256 = "4d1891c5acb5d9ecb6d1a8c3e5f2d57ee730b9408715d66ab1a3d640dedc30e9",
            files = listOf(
                PocketSpeechArtifact(
                    "MODEL_LICENSE.txt", 1_810L,
                    "590118cb876598e06aa772e94d1dbf8e1bd712bc0dd81eac7fabcf9759ebcbbe",
                ),
                PocketSpeechArtifact(
                    "VOICE_ATTRIBUTION.md", 2_001L,
                    "a0b0bdc0c2d5d0736b8fe15ec5ba4cb6c107e131eb00c1d0e3dbf1026456bca8",
                ),
                PocketSpeechArtifact(
                    "manifest.json", 340L,
                    "6fb3793cee1b28acff30de8fee5f005bc253cf2d112113d3e43b0a0603d7d592",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_flow.onnx", 39_081_891L,
                    "37edf37a46e35374956d6a083ef257cb1b43523aac9d84e6034671d923a70c77",
                ),
                PocketSpeechArtifact(
                    "models/flow_lm_main.onnx", 302_364_168L,
                    "9ba75f4143bda06ef6f31f7b92838af72e827943f8569251bcf0e134602de42d",
                ),
                PocketSpeechArtifact(
                    "models/mimi_decoder.onnx", 41_412_445L,
                    "c3d6165f102680f5529b919428463e4a42e15ba6a4bcb355505fdc6c2128d798",
                ),
                PocketSpeechArtifact(
                    "models/mimi_encoder.onnx", 39_283_560L,
                    "cc448c2504ad6bbfe3d0e78da987d2cd93206626ec90a525eccc2e50f7f23a09",
                ),
                PocketSpeechArtifact(
                    "models/text_conditioner.onnx", 16_388_310L,
                    "bb588683cbe8f0f6c69c0e77b9dc113865fc850ef5b28907a71330808c478584",
                ),
                PocketSpeechArtifact(
                    "models/tokenizer.model", 60_995L,
                    "3aa51309c55f114771c156aaeb86f6fc325991364aa3c38af74aecf1cbd0fade",
                ),
                PocketSpeechArtifact(
                    "voices/rafael.wav", 480_078L,
                    "261a662e9a9f9240ca96e9fa5898b4c58f4e3107182dc501182ea7938d6aece9",
                ),
            ),
        ),
    )

    fun find(code: String): PocketSpeechLanguage? = languages.firstOrNull { it.code == code }
}
