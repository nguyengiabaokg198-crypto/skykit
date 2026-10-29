#!/usr/bin/env python3
"""SkyKit textures: 3 item textures (16x16) + mod icon (128x128). RGBA padded."""
import struct, zlib, os

def chunk(tag, data):
    c = struct.pack('>I', len(data)) + tag + data
    return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

def png(path, w, h, pixel):
    raw = b''
    for y in range(h):
        raw += b'\x00'
        for x in range(w):
            raw += bytes(pixel(x, y))
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(raw)))
        f.write(chunk(b'IEND', b''))

T = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'skykit')

# ---------------- controller: navy remote, green screen, gold buttons ---------------
def controller(x, y):
    if 4 <= x <= 11 and 2 <= y <= 13:
        if x in (4, 11) or y in (2, 13):
            return (16, 22, 44, 255)          # outline
        if 5 <= x <= 10 and 4 <= y <= 6:
            return (90, 232, 130, 255)        # green screen
        if 5 <= x <= 10 and 5 == y:
            return (40, 160, 80, 255)
        if y in (9, 11) and x in (6, 7, 9, 10):
            return (240, 200, 60, 255)        # gold buttons
        return (30, 40, 70, 255)              # body
    if x in (7, 8) and y in (0, 1):
        return (200, 210, 230, 255)           # antenna tip
    return (0, 0, 0, 0)

# ---------------- camera: gray body, cyan lens, orange shutter ---------------------
def camera(x, y):
    if 5 <= x <= 10 and y in (3, 4):
        return (70, 76, 84, 255)              # top bump
    if 2 <= x <= 13 and 5 <= y <= 12:
        if x in (2, 13) or y in (5, 12):
            return (60, 64, 70, 255)          # outline
        d = ((x - 7.5) ** 2 + (y - 8.5) ** 2) ** 0.5
        if d <= 1.2:
            return (140, 240, 255, 255)       # lens glint
        if d <= 2.8:
            return (25, 30, 40, 255)          # lens
        if x == 12 and y == 6:
            return (230, 80, 60, 255)         # record dot
        return (120, 126, 134, 255)           # body
    return (0, 0, 0, 0)

# ---------------- paper: blueprint sheet with white grid + house --------------------
def paper(x, y):
    if 2 <= x <= 13 and 2 <= y <= 13:
        if x in (2, 13) or y in (2, 13):
            return (70, 120, 200, 255)        # border
        if x % 4 == 0 or y % 4 == 0:
            return (110, 150, 215, 255)       # grid
        # house body outline (white)
        if 4 <= x <= 11 and 8 <= y <= 11 and (x in (4, 11) or y == 11):
            return (240, 248, 255, 255)
        # roof (red triangle)
        if 6 <= y <= 7 and abs(x - 7.5) <= (y - 5):
            return (210, 70, 60, 255)
        return (40, 80, 160, 255)             # blueprint blue
    return (0, 0, 0, 0)

# ---------------- icon: blueprint sheet + bridge over the bottom -------------------
def icon(x, y):
    if x < 6 or x > 121 or y < 6 or y > 121:
        return (10, 16, 24, 255)
    # blueprint sheet
    if 20 <= x <= 107 and 18 <= y <= 92:
        if x in (20, 107) or y in (18, 92):
            return (90, 140, 220, 255)
        if x % 16 == 0 or y % 16 == 0:
            return (60, 100, 180, 255)        # grid
        # house outline on the sheet
        if 36 <= x <= 91 and 50 <= y <= 78 and (x in (36, 91) or y == 78):
            return (240, 248, 255, 255)
        if 50 <= y <= 62 and abs(x - 63) <= (y - 50) * 1.6:
            edge = abs(x - 63) >= (y - 50) * 1.6 - 4
            if edge or y <= 53:
                return (215, 75, 65, 255)     # red roof
        return (40, 80, 160, 255)             # sheet blue
    # bridge deck + pillars (green)
    if 100 <= y <= 106 and 14 <= x <= 113:
        return (90, 232, 130, 255)
    if (30 <= x <= 36 or 91 <= x <= 97) and 106 <= y <= 114:
        return (90, 232, 130, 255)
    if 14 <= x <= 113 and y in (107, 108):
        return (60, 190, 100, 255)            # deck shadow
    return (24, 34, 56, 255)                  # navy bg

png(os.path.join(T, 'textures', 'item', 'skykit_controller.png'), 16, 16, controller)
png(os.path.join(T, 'textures', 'item', 'blueprint_camera.png'), 16, 16, camera)
png(os.path.join(T, 'textures', 'item', 'blueprint_paper.png'), 16, 16, paper)
png(os.path.join(T, 'icon.png'), 128, 128, icon)
print('textures written')
