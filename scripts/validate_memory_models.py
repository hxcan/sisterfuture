"""Desktop smoke test, not a substitute for Android/ARM instrumentation tests.

Python 3.12: pip install ai-edge-litert==1.2.0 tensorflow-cpu==2.16.1 tokenizers==0.23.2
TensorFlow supplies the same-version Flex ops as the Android dependency.
"""
from pathlib import Path
import hashlib
import json
import numpy as np
import tensorflow as tf  # Loads Flex ops needed by FastSpeech2.
from ai_edge_litert.interpreter import Interpreter
from tokenizers import Tokenizer

assets = Path(__file__).resolve().parents[1] / "app/src/main/assets"
expected = {
    "bge-small-zh-v1.5.tflite": "c5fe04dc660ff652d7715e0fd2fc5957766e30f8456e1cf7d41defd7c95f8954",
    "tokenizer.json": "48cea5d44424912a6fd1ea647bf4fe50b55ab8b1e5879c3275f80e339e8fae26",
}
for name, digest in expected.items():
    assert hashlib.sha256((assets / "memory" / name).read_bytes()).hexdigest() == digest
tokenizer = Tokenizer.from_file(str(assets / "memory/tokenizer.json"))
tokenizer.enable_truncation(512)
tokenizer.enable_padding(length=512)
interpreter = Interpreter(model_path=str(assets / "memory/bge-small-zh-v1.5.tflite"), num_threads=2)
run = interpreter.get_signature_runner()
vectors = []
for text in ["我喜欢喝咖啡", "我爱喝咖啡", "服务器的登录密码"]:
    encoding = tokenizer.encode(text)
    result = run(args_0=np.array([encoding.ids], np.int32),
                 args_1=np.array([encoding.attention_mask], np.int32))
    vector = result["last_hidden_state"][0, 0]
    assert vector.shape == (512,) and np.isfinite(vector).all()
    vectors.append(vector / np.linalg.norm(vector))
similar = float(vectors[0] @ vectors[1])
unrelated = float(vectors[0] @ vectors[2])
assert similar > unrelated + 0.3
print(f"BGE: 512-dimensional normalized vectors; similar={similar:.3f}, unrelated={unrelated:.3f}")

mapping = json.loads((assets / "baker_mapper.json").read_text())["symbol_to_id"]
ids = np.array([[mapping[s] for s in ["n", "i3", "h", "ao3", "#3"]]], np.int32)
tts = Interpreter(model_path=str(assets / "fastspeech2_quan.tflite"), num_threads=2)
tts.resize_tensor_input(0, ids.shape)
tts.allocate_tensors()
for index, value in enumerate([ids, np.array([0], np.int32), *[np.array([1.0], np.float32) for _ in range(3)]]):
    tts.set_tensor(tts.get_input_details()[index]["index"], value)
tts.invoke()
mel = tts.get_tensor(tts.get_output_details()[0]["index"])
assert mel.size and np.isfinite(mel).all()
vocoder = Interpreter(model_path=str(assets / "mb_melgan_new.tflite"), num_threads=2)
vocoder.resize_tensor_input(0, mel.shape)
vocoder.allocate_tensors()
vocoder.set_tensor(vocoder.get_input_details()[0]["index"], mel)
vocoder.invoke()
audio = vocoder.get_tensor(vocoder.get_output_details()[0]["index"])
assert audio.size and np.isfinite(audio).all()
print(f"TTS: mel={mel.shape}, audio={audio.shape}, finite output")
