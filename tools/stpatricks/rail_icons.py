"""
Benflix St. Patrick's Day: the rail and phone-tab icons for the new navigation (the six approved door charms,
cut tight and set in a square so they fill an icon's box). Run from the repository root after pictures.js:
    python3 tools/stpatricks/rail_icons.py
Writes app/src/main/res/drawable-nodpi/sp_icon_<door>.png and server/web/img/sp/sp_icon_<door>.png.
"""
from PIL import Image

SIDE = 128
for door in ["movies", "shows", "live", "games", "music", "books"]:
    im = Image.open(f"app/src/main/res/drawable-nodpi/sp_charm_{door}.png").convert("RGBA")
    im = im.crop(im.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox())
    im.thumbnail((SIDE, SIDE), Image.LANCZOS)
    out = Image.new("RGBA", (SIDE, SIDE))
    out.alpha_composite(im, ((SIDE - im.width) // 2, (SIDE - im.height) // 2))
    for d in ["app/src/main/res/drawable-nodpi", "server/web/img/sp"]:
        out.save(f"{d}/sp_icon_{door}.png", optimize=True)
    print(door, im.size)
