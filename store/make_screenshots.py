"""Play Store screenshots (1080x1920). Before/after pairs are real outputs of the app's AI pipeline."""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

W, H = 1080, 1920
F = '/usr/share/fonts/truetype/google-fonts/'
bold = lambda s: ImageFont.truetype(F + 'Poppins-Bold.ttf', s)
med = lambda s: ImageFont.truetype(F + 'Poppins-Medium.ttf', s)
PINK, PURPLE, BLUE, GOLD = (224, 64, 251), (180, 76, 255), (79, 124, 255), (255, 200, 61)
LOGO = sys.argv[1]
OUT = sys.argv[2]
SHOTS = sys.argv[3]


def gradient(w, h, cols, horizontal=True):
    g = Image.new('RGB', (w, h))
    px = g.load()
    n = len(cols) - 1
    for i in range(w if horizontal else h):
        t = i / max(1, (w if horizontal else h) - 1) * n
        k = min(int(t), n - 1); f = t - k
        c = tuple(int(cols[k][j] + (cols[k + 1][j] - cols[k][j]) * f) for j in range(3))
        if horizontal:
            for y in range(h): px[i, y] = c
        else:
            for x in range(w): px[x, i] = c
    return g


def background():
    bg = gradient(W, H, [(26, 12, 58), (10, 7, 26), (5, 4, 10)], horizontal=False).convert('RGBA')
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((-200, -350, 900, 600), fill=(180, 76, 255, 110))
    d.ellipse((500, 1300, 1400, 2200), fill=(79, 124, 255, 70))
    return Image.alpha_composite(bg, glow.filter(ImageFilter.GaussianBlur(160)))


def text_center(img, y, text, font, fill=(255, 255, 255)):
    d = ImageDraw.Draw(img)
    w = d.textlength(text, font=font)
    d.text(((W - w) / 2, y), text, font=font, fill=fill)


def gradient_text_center(img, y, text, font):
    d = ImageDraw.Draw(img)
    w = int(d.textlength(text, font=font)); h = font.size + 40
    mask = Image.new('L', (w, h), 0)
    ImageDraw.Draw(mask).text((0, 0), text, font=font, fill=255)
    g = gradient(w, h, [PINK, PURPLE, BLUE]).convert('RGBA')
    img.paste(g, ((W - w) // 2, y), mask)


def rounded(im, r):
    m = Image.new('L', im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    out = im.convert('RGBA'); out.putalpha(m)
    return out


def cover(im, w, h, cx=0.5, cy=0.5, zoom=1.0):
    s = max(w / im.width, h / im.height) * zoom
    im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    x = int(min(max(cx * im.width - w / 2, 0), im.width - w))
    y = int(min(max(cy * im.height - h / 2, 0), im.height - h))
    return im.crop((x, y, x + w, y + h))


def pill(img, xy, text, font, bg, fg, anchor_right=False):
    d = ImageDraw.Draw(img)
    tw = d.textlength(text, font=font); pw, ph = int(tw + 48), font.size + 26
    x, y = xy
    if anchor_right: x -= pw
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle((x, y, x + pw, y + ph), ph // 2, fill=bg)
    img.alpha_composite(layer)
    ImageDraw.Draw(img).text((x + 24, y + 8), text, font=font, fill=fg)


def before_after(before, after, w, h, cx, cy, zoom, split=0.5):
    b = cover(before.resize(after.size, Image.BICUBIC), w, h, cx, cy, zoom)
    a = cover(after, w, h, cx, cy, zoom)
    sx = int(w * split)
    card = a.copy(); card.paste(b.crop((0, 0, sx, h)), (0, 0))
    card = card.convert('RGBA')
    d = ImageDraw.Draw(card)
    d.rectangle((sx - 3, 0, sx + 3, h), fill=(255, 255, 255))
    cy2 = h // 2
    d.ellipse((sx - 44, cy2 - 44, sx + 44, cy2 + 44), fill=(255, 255, 255))
    d.ellipse((sx - 36, cy2 - 36, sx + 36, cy2 + 36), fill=PURPLE)
    d.polygon([(sx - 8, cy2 - 14), (sx - 24, cy2), (sx - 8, cy2 + 14)], fill='white')
    d.polygon([(sx + 8, cy2 - 14), (sx + 24, cy2), (sx + 8, cy2 + 14)], fill='white')
    pill(card, (24, 24), 'BEFORE', bold(30), (7, 6, 15, 190), (255, 255, 255))
    pill(card, (w - 24, 24), 'AFTER  4K', bold(30), (7, 6, 15, 190), GOLD, anchor_right=True)
    return rounded(card, 44)


def shadow_paste(img, card, x, y, color=(180, 76, 255, 150), blur=40, grow=10):
    sh = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((x - grow, y - grow, x + card.width + grow, y + card.height + grow), 60, fill=color)
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))
    img.alpha_composite(card, (x, y))


def footer(img):
    logo = rounded(Image.open(LOGO).convert('RGB').resize((104, 104), Image.LANCZOS), 24)
    d = ImageDraw.Draw(img)
    f = bold(44); name = 'Image Enhancer Pro'
    tw = d.textlength(name, font=f)
    total = 104 + 24 + tw + 20 + 150
    x = int((W - total) / 2); y = H - 160
    img.alpha_composite(logo, (x, y))
    d.text((x + 128, y + 22), name, font=f, fill='white')
    gx = int(x + 128 + tw + 20)
    gold = gradient(150, 58, [(255, 224, 122), GOLD, (255, 166, 43)])
    img.paste(rounded(gold, 18), (gx, y + 24), rounded(gold, 18))
    ImageDraw.Draw(img).text((gx + 22, y + 26), 'AI 4K', font=bold(36), fill=(7, 6, 15))


def free_badge(img):
    """Gold "100% FREE" pill at the top."""
    f = bold(40); d = ImageDraw.Draw(img)
    text = '100% FREE'
    tw = int(d.textlength(text, font=f)); pw, ph = tw + 70, 74
    g = gradient(pw, ph, [(255, 224, 122), GOLD, (255, 166, 43)])
    x = (W - pw) // 2; y = 22
    img.paste(rounded(g, ph // 2), (x, y), rounded(g, ph // 2))
    ImageDraw.Draw(img).text((x + 35, y + 11), text, font=f, fill=(7, 6, 15))


def slide(name, line1, line2, sub, card):
    img = background()
    text_center(img, 118, line1, bold(84))
    gradient_text_center(img, 220, line2, bold(94))
    text_center(img, 356, sub, med(40), fill=(207, 201, 234))
    shadow_paste(img, card, (W - card.width) // 2, 470)
    free_badge(img)
    footer(img)
    img.convert('RGB').save(f'{OUT}/{name}.png')


def phone(shot):
    s = Image.open(shot).convert('RGB')
    s = s.resize((700, int(700 * s.height / s.width)), Image.LANCZOS)
    s = s.crop((0, 0, 700, min(s.height, 1240)))
    frame = Image.new('RGBA', (s.width + 36, s.height + 36), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, frame.width - 1, frame.height - 1), 70, fill=(40, 34, 70))
    frame.alpha_composite(rounded(s, 54), (18, 18))
    return frame


if __name__ == '__main__':
    pairs = dict(arg.split('=') for arg in sys.argv[4:])
    L = lambda k: Image.open(pairs[k]).convert('RGB')
    if 'girl_in' in pairs:
        slide('01_face', 'Blurry Photo?', 'Crystal Clear!', '100% free AI enhancer • 1 tap to 4K',
              before_after(L('girl_in'), L('girl_out'), 960, 1300, 0.55, 0.42, 1.0, split=0.6))
    slide('02_hero', 'Old & Blurry?', 'Make it 4K', 'One tap. Sharp, clear & detailed.',
          before_after(L('cat_in'), L('cat_out'), 960, 1300, 0.42, 0.45, 1.25))
    slide('03_detail', 'Sharpen Every', 'Tiny Detail', 'Removes blur, noise & JPEG marks',
          before_after(L('coffee_in'), L('coffee_out'), 960, 1300, 0.5, 0.5, 1.15))
    slide('04_upscale', 'Upscale to', 'Ultra HD 4K', 'Up to 4096 px, same shape as your photo',
          before_after(L('girl_in'), L('girl_out'), 960, 1300, 0.78, 0.17, 2.4, split=0.5))
    slide('05_app', 'One Tap.', "That's It!", 'Upload  •  Enhance  •  Save in 4K', phone(f'{SHOTS}/store_result.png'))
    slide('06_private', '100% Private', 'On Your Phone', 'Your photos are never uploaded anywhere', phone(f'{SHOTS}/03_working.png'))


def feature_graphic(before, after, out):
    """Play Store feature graphic 1024x500."""
    global W, H
    W, H = 1024, 500
    img = background()
    card = before_after(before, after, 430, 440, 0.6, 0.35, 1.3, split=0.5)
    shadow_paste(img, card, 560, 30, blur=30)
    logo = rounded(Image.open(LOGO).convert('RGB').resize((120, 120), Image.LANCZOS), 28)
    img.alpha_composite(logo, (50, 50))
    d = ImageDraw.Draw(img)
    d.text((50, 190), 'Image Enhancer', font=bold(56), fill='white')
    m = Image.new('L', (480, 80), 0); ImageDraw.Draw(m).text((0, 0), 'Pro  AI 4K', font=bold(56), fill=255)
    img.paste(gradient(480, 80, [PINK, PURPLE, BLUE]), (50, 255), m)
    d.text((52, 340), 'Blurry photo to sharp 4K in 1 tap', font=med(26), fill=(207, 201, 234))
    g = gradient(230, 62, [(255, 224, 122), GOLD, (255, 166, 43)])
    img.paste(rounded(g, 31), (50, 395), rounded(g, 31))
    ImageDraw.Draw(img).text((75, 404), '100% FREE', font=bold(34), fill=(7, 6, 15))
    img.convert('RGB').save(out)
    W, H = 1080, 1920


if __name__ == '__main__' and 'girl_in' in dict(a.split('=') for a in sys.argv[4:]):
    p = dict(a.split('=') for a in sys.argv[4:])
    feature_graphic(Image.open(p['girl_in']).convert('RGB'), Image.open(p['girl_out']).convert('RGB'), f'{OUT}/../feature_graphic_1024x500.png')
