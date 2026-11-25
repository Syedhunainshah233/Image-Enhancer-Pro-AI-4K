"""Downloads the official model weights and converts them to the ONNX files the app uses.

Run by GitHub Actions (cached). Output: models/src/main/assets/{esrgan_x4v3,gfpgan_v14}.onnx
  - Real-ESRGAN general x4v3 (BSD-3-Clause)   github.com/xinntao/Real-ESRGAN
  - GFPGAN v1.4 (Apache-2.0)                  github.com/TencentARC/GFPGAN
Big weights are stored as fp16 with a Cast to fp32 in front (ONNX Runtime folds it at load),
so the files are half the size while the maths stays fp32.
"""
import os, sys, urllib.request
import numpy as np, torch, spandrel, onnx, onnxruntime as ort
from onnx import helper, numpy_helper, TensorProto

OUT = sys.argv[1] if len(sys.argv) > 1 else 'models/src/main/assets'
TMP = os.environ.get('RUNNER_TEMP', '/tmp')
URLS = {
    'x4v3.pth': 'https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth',
    'gfp.pth': 'https://github.com/TencentARC/GFPGAN/releases/download/v1.3.0/GFPGANv1.4.pth',
}
os.makedirs(OUT, exist_ok=True)
for name, url in URLS.items():
    p = os.path.join(TMP, name)
    if not os.path.exists(p):
        print('download', url); urllib.request.urlretrieve(url, p)


class G(torch.nn.Module):
    def __init__(s, m): super().__init__(); s.m = m
    def forward(s, x):  # [0,1] in and out
        y = s.m(x * 2 - 1, return_rgb=False, randomize_noise=False)[0]
        return ((y + 1) / 2).clamp(0, 1)


class E(torch.nn.Module):
    def __init__(s, m): super().__init__(); s.m = m
    def forward(s, x): return s.m(x).clamp(0, 1)


def half_store(src, dst):
    m = onnx.load(src); g = m.graph; inits, casts = [], []
    for init in g.initializer:
        a = numpy_helper.to_array(init)
        if a.dtype == np.float32 and a.size >= 1024:
            h = numpy_helper.from_array(a.astype(np.float16), init.name + '_fp16')
            inits.append(h); casts.append(helper.make_node('Cast', [h.name], [init.name], to=TensorProto.FLOAT))
        else:
            inits.append(init)
    del g.initializer[:]; g.initializer.extend(inits)
    old = list(g.node); del g.node[:]; g.node.extend(casts + old)
    onnx.save(m, dst)


def export(module, example, name, dynamic):
    tmp = os.path.join(TMP, name + '.fp32.onnx'); dst = os.path.join(OUT, name)
    kw = dict(dynamic_axes={'input': {2: 'h', 3: 'w'}, 'output': {2: 'H', 3: 'W'}}) if dynamic else {}
    torch.onnx.export(module, example, tmp, input_names=['input'], output_names=['output'], opset_version=17, dynamo=False, **kw)
    half_store(tmp, dst)
    # Check: ONNX output matches PyTorch.
    x = torch.rand(*example.shape)
    with torch.no_grad(): ref = module(x).numpy()
    got = ort.InferenceSession(dst).run(None, {'input': x.numpy()})[0]
    err = float(np.abs(ref - got).mean())
    print(name, os.path.getsize(dst) // 1_000_000, 'MB, mean abs diff', err)
    assert err < 0.01, err


torch.manual_seed(0)
esr = spandrel.ModelLoader().load_from_file(os.path.join(TMP, 'x4v3.pth')).model.eval()
export(E(esr), torch.rand(1, 3, 64, 64), 'esrgan_x4v3.onnx', True)
gfp = spandrel.ModelLoader().load_from_file(os.path.join(TMP, 'gfp.pth')).model.eval()
export(G(gfp), torch.rand(1, 3, 512, 512), 'gfpgan_v14.onnx', False)
