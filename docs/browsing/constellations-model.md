# Constellations local embedding model

| Contract | Value |
| --- | --- |
| Publisher / model | [Sentence Transformers / distiluse-base-multilingual-cased-v2](https://huggingface.co/sentence-transformers/distiluse-base-multilingual-cased-v2) |
| Immutable revision | `bfe45d0732ca50787611c0fe107ba278c7f3f889` |
| Model license | Apache License 2.0; attribution: Nils Reimers and Iryna Gurevych, Sentence Transformers |
| Runtime | `com.microsoft.onnxruntime:onnxruntime-android:1.24.3`, MIT license |
| Language support | Multilingual model, including German and English |
| Output | 512 floats, L2-normalized; cosine similarity represents semantic relatedness, not factual agreement |
| Pipeline | Cased BERT basic tokenization + WordPiece, 128-token truncation, quantized DistilBERT CPU inference, masked mean pooling, published 768→512 dense layer with tanh, normalization |
| Content bound | At most 256 title characters and 16,384 body characters accepted; title capped at 24 tokens. Short pages use one inference; longer pages sample beginning, middle, and end with at most three 128-token windows. Normalized window vectors are averaged and normalized again. |
| Model download | Explicit user action only; approximately 138 MB; immutable HTTPS URLs, exact byte counts and SHA-256 verified before atomic rename |
| Network boundary | Downloader accepts only progress callback; no URL, title, page text, or profile input; inference never contacts remote service |
| Storage | Public model assets shared under app-private `noBackupFilesDir/constellation_model`; browsing vectors and graph data remain isolated by profile |
| Runtime resources | One loaded CPU session per embedder; two intra-op threads, one inter-op thread; tensors/results closed after each inference, session closed with owner |
| Release shrinking | Keep `ai.onnxruntime.**` per [ONNX Runtime Android guidance](https://onnxruntime.ai/docs/build/android.html) |

All files use `https://huggingface.co/sentence-transformers/distiluse-base-multilingual-cased-v2/resolve/bfe45d0732ca50787611c0fe107ba278c7f3f889/`:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `onnx/model_qint8_arm64.onnx` | 135,336,307 | `c78c64bb0446ceb7086d2de24c2f4297e7a909eb457b4f68e5fdb6ab19f285c0` |
| `vocab.txt` | 995,526 | `fe0fda7c425b48c516fc8f160d594c8022a0808447475c1a7c6d6479763f310c` |
| `2_Dense/model.safetensors` | 1,575,104 | `0a21b1ce908e772ebf09f93c20ca09524c32706e9918d9c0169a3f0663b191ed` |

Model source: [pinned repository](https://huggingface.co/sentence-transformers/distiluse-base-multilingual-cased-v2/tree/bfe45d0732ca50787611c0fe107ba278c7f3f889). Architecture and tokenizer settings derive from its model card, `sentence_bert_config.json`, `tokenizer_config.json`, `1_Pooling/config.json`, and `2_Dense/config.json`. ONNX graph inputs are `input_ids` and `attention_mask`; output is `last_hidden_state`. The released ONNX artifact contains the Transformer stage only: applying the separate dense layer is required for the published sentence embedding.

Runtime references: [Java API](https://onnxruntime.ai/docs/get-started/with-java.html), [tensor ownership](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OnnxTensor.html), [Sentence-BERT paper](https://arxiv.org/abs/1908.10084).
