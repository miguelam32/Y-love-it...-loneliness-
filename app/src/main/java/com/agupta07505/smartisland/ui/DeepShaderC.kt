package com.agupta07505.smartisland.ui

internal object DeepShaderC {
    const val SRC = """
// FRONT: thick white tide band (dense beads stacked) + needles
float3 tide(float x, float y, float4 e, float bv, float pxy) {
    float t = uT.x;
    float bass = uAux.x;
    float mid = uAux.y;
    float hi = uAux.z;
    float swell = 0.16 * sin(x * 3.4 + t * 0.55 + 1.3) * (0.5 + 0.9 * bass)
        + 0.08 * sin(x * 6.2 - t * 0.8) * (0.4 + mid)
        + 0.04 * sin(x * 10.0 + t * 1.3) * (0.3 + 2.0 * hi);
    float yT = -0.06 + swell + e.x * 0.08;
    float hw = 0.075 + 0.20 * clamp(e.y * 1.4, 0.0, 1.0) + 0.05 * bv;
    float cw = uRes.y * 0.028;
    float ci = floor(x * uRes.x / cw);
    float s = 0.0;
    float wsp = 0.0;
    float inb = step(abs(y - yT), hw + 8.0 * pxy);
    for (int j = 0; j < 6; j++) {
      if (inb > 0.5 && float(j) < 6.0 * uQ + 0.01) {
        float2 id = float2(ci, float(j) + 131.0);
        float h1 = hash(id);
        float h2 = hash(id + 2.3);
        float h3 = hash(id + 6.1);
        float bxn = (ci + 0.2 + 0.6 * h1) * cw / uRes.x;
        float ddx = (x - bxn) * uRes.x;
        float yb = yT + hw * (1.0 - 2.0 * h2 * h2);
        float ddy = (y - yb) / pxy;
        float r = 0.65 + 0.55 * h3 * h3;
        float tw = 0.75 + 0.25 * sin(t * (2.0 + 4.0 * h3) + h2 * 40.0);
        float bl = exp(-(ddx * ddx + ddy * ddy) / (r * r) * 1.3) * tw;
        s += bl * (0.55 + 0.7 * (1.0 - h2 * h2));
        wsp += bl * h1 * h1 * h1;
      }
    }
    float dyt = (y - (yT + hw * 0.55)) / (hw + 0.03);
    float glow = exp(-dyt * dyt * 1.8);
    float lvl = 0.75 + 0.9 * bass + 0.5 * mid + 0.5 * bv;
    float under = max(0.0, (yT + hw) - y);
    float body = exp(-under * 1.25) * step(y, yT + hw) * smoothstep(-1.05, -0.30, y + 0.55);
    float3 base = mix(float3(0.62, 0.70, 1.0), float3(0.97, 0.98, 1.0), 0.75);
    float3 col = base * s * 0.75 * lvl + float3(1.0) * wsp * 0.5 + mix(float3(0.45, 0.55, 1.0), float3(0.75, 0.55, 1.0), 0.5 + 0.5 * x) * glow * 0.62 * lvl + float3(0.17, 0.22, 0.62) * body * (0.55 + 0.5 * lvl);

    // needles: band driven, up through the sky and down through the terrain
    float nn = 64.0;
    float idx = floor(x * nn);
    float hs = hash(float2(idx, 3.0));
    float hs2 = hash(float2(idx, 11.0));
    float bvn = bandAt((idx + 0.5) / nn);
    float act = step(0.38, hs);
    float len = (0.04 + bvn) * (0.25 + 0.75 * hs2) * 1.0 * act * (0.50 + 0.9 * bass);
    float dxp = (fract(x * nn) - 0.5) * uRes.x / nn;
    float thin = exp(-dxp * dxp * 2.4);
    float top = yT + hw;
    float up = step(top, y) * step(y, top + len) * (1.0 - (y - top) / max(len, 0.03));
    float bot = yT - hw;
    float dlen = len * 1.6;
    float dn = step(y, bot) * step(bot - dlen, y) * (1.0 - (bot - y) / max(dlen, 0.03)) * 0.40;
    float dots = 0.55 + 0.45 * step(0.5, fract(y * uRes.y * 0.30 + hs * 7.0));
    col += float3(0.74, 0.90, 1.0) * thin * (up + dn) * dots * 1.35;
    return col;
}

float3 layers(float x, float y, float4 e, float bv) {
    float pxy = 2.0 / uRes.y;
    float3 col = aurora(x, y, e, bv, pxy);
    col += terrain(x, y, bv, pxy);
    col += tide(x, y, e, bv, pxy);
    return col;
}

float3 scene(float2 uv, float chroma) {
    float x = uv.x;
    float y = (0.5 - uv.y) * 2.0;
    float beat = uAux.w;
    float glt = step(0.5, beat) * beat;
    float sl = floor(uv.y * 9.0);
    float xg = clamp(x + (hash(float2(sl, floor(uT.x * 24.0))) - 0.5) * 0.06 * glt, 0.0, 1.0);
    float4 e = envAt(xg);
    float bv = bandAt(xg);
    float3 col;
    if (chroma > 0.0005) {
        float3 rc = layers(xg + chroma, y, e, bv);
        float3 gc = layers(xg, y, e, bv);
        float3 bc = layers(xg - chroma, y, e, bv);
        col = float3(rc.x, gc.y, bc.z);
    } else {
        col = layers(xg, y, e, bv);
    }

    // background haze (deep indigo, like the photo)
    float3 hz = float3(0.020, 0.020, 0.090) + float3(0.040, 0.030, 0.170) * (0.35 + 0.65 * smoothstep(-1.0, 0.6, y)) * (0.6 + 0.6 * x);
    col += hz * (0.55 + 0.7 * uAux.x);

    // beat: tick through the pill + wide halo + expanding light ring
    float mk = e.z * e.z * (1.0 - smoothstep(0.7, 1.0, abs(y)));
    col += float3(1.0, 0.92, 0.80) * mk * 1.1;
    col += gradc(x * 0.8 + 0.2) * e.w * e.w * (0.45 + 0.5 * exp(-y * y * 1.5));
    float3 bu = bursts(x, y, -0.05);
    col += float3(0.70, 0.55, 1.0) * bu.x * 0.9 + float3(1.0, 0.97, 1.0) * bu.y * 1.6;

    // deep field: bokeh + stars
    float2 bp = float2(uv.x * uRes.x, uv.y * uRes.y) / (uRes.y * 0.30);
    float2 bi = floor(bp);
    float bh = hash(bi + 17.0);
    float2 bo = float2(0.25 + 0.5 * hash(bi + 3.0), 0.25 + 0.5 * hash(bi + 9.0));
    float bd = length(fract(bp) - bo) / (0.10 + 0.10 * hash(bi + 5.0));
    col += float3(0.55, 0.60, 1.0) * step(0.55, bh) * exp(-bd * bd * 1.6) * 0.07 * (0.6 + 0.4 * sin(uT.x * 0.7 + bh * 30.0));
    float2 sp = floor(uv * uRes * 0.5);
    float sh2 = hash(sp);
    float stw = 0.5 + 0.5 * sin(uT.x * (1.0 + sh2 * 4.0) + sh2 * 60.0);
    col += float3(0.7, 0.8, 1.0) * step(0.985, sh2) * stw * 0.40;
    col += gradc(x + uT.x * 0.03) * (beat * beat) * 0.40 * exp(-y * y * 0.8);
    return col;
}

"""
}
