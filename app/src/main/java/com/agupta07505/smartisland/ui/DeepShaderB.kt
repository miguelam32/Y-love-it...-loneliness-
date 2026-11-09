package com.agupta07505.smartisland.ui

internal object DeepShaderB {
    const val SRC = """
// BACK: aurora particle cloud (violet beads) + thin PCM line
float3 aurora(float x, float y, float4 e, float bv, float pxy) {
    float t = uT.x;
    float bass = uAux.x;
    float mid = uAux.y;
    float hi = uAux.z;
    float ya = 0.56 + 0.15 * sin(x * 13.0 + t * 0.34 + 0.6) * (0.55 + 0.9 * bass)
        + 0.09 * sin(x * 27.0 - t * 0.55 + 1.9) * (0.40 + 1.0 * mid)
        + 0.045 * sin(x * 46.0 + t * 0.9) * (0.30 + 2.0 * hi) + e.x * 0.12;
    float sg = 0.11 + 0.20 * clamp(e.y * 1.6, 0.0, 1.0) + 0.10 * bv;
    float cw = uRes.y * 0.040;
    float n = uRes.x / cw;
    float drift = t * 0.9;
    float c0 = floor(x * n + drift);
    float inten = 0.0;
    float white = 0.0;
    float inb = step(abs(y - ya), sg * 1.5 + 8.0 * pxy);
    for (int dxi = 0; dxi < 3; dxi++) {
        float ci = c0 + float(dxi) - 1.0;
        for (int j = 0; j < 7; j++) {
          if (inb > 0.5 && float(j) < 7.0 * uQ + 0.01) {
            float2 id = float2(ci, float(j) + 31.0);
            float h1 = hash(id);
            float h2 = hash(id + 3.7);
            float h3 = hash(id + 8.9);
            float pxn = (ci + h1 - drift) / n;
            float tk = h2 + h3 - 1.0;
            float py = ya + tk * sg * (1.0 + 0.35 * sin(t * (0.5 + h1) + h2 * 30.0));
            float ddx = (x - pxn) * uRes.x;
            float ddy = (y - py) / pxy;
            float r = 0.55 + 0.9 * h3 * h3;
            float b = 0.25 + 1.9 * h1 * h1 * h1;
            float tw = 0.65 + 0.35 * sin(t * (2.0 + 5.0 * h3) + h2 * 40.0);
            float bl = exp(-(ddx * ddx + ddy * ddy) / (r * r) * 1.4);
            float dens = 1.0 - 0.55 * abs(tk);
            inten += bl * b * tw * dens;
            white += bl * h1 * h1 * h1 * h1 * tw;
          }
        }
    }
    float lvl = 0.55 + 0.9 * bass + 0.7 * mid + 0.6 * bv;
    float3 cc = gradc(x * 0.95 + 0.04 * sin(t * 0.2));
    float dyc = (y - ya) / (sg + 0.03);
    float qg = 1.0 + 0.8 * (1.0 - uQ);
    float3 col = cc * inten * 0.62 * qg * lvl + float3(0.90, 0.95, 1.0) * white * 0.9 * qg * lvl;
    col += cc * exp(-dyc * dyc * 1.2) * 0.16 * lvl;
    return col;
}

// BACK LINES: the real PCM waveform (zero-cross triggered, slowly smoothed) as glowing neon lines
float3 scopeLines(float x, float y, float pxy, float act) {
    float yc = 0.30;
    float amp = 0.66;
    float3 col = float3(0.0);
    if (abs(y - yc) < amp + 0.34) {
        float4 s = scopeAt(x);
        float sp = 0.5 * uRes.y / uRes.x;
        float ya = yc + amp * s.x;
        float sa = s.y * amp * sp;
        float da = (y - ya) / pxy / sqrt(1.0 + sa * sa);
        float ab = 0.75 * amp;
        float yb = yc + 0.03 + ab * s.z;
        float sb = s.w * ab * sp;
        float db = (y - yb) / pxy / sqrt(1.0 + sb * sb);
        float3 ca = mix(float3(0.30, 0.92, 1.0), float3(0.72, 0.50, 1.0), x);
        float3 cb = mix(float3(0.62, 0.40, 1.0), float3(0.25, 0.75, 1.0), x);
        col += ca * (exp(-da * da * 0.34) * 0.90 + exp(-da * da * 0.045) * 0.34 + exp(-da * da * 0.004) * 0.06);
        col += float3(0.90, 0.97, 1.0) * exp(-da * da * 1.8) * 0.55;
        col += cb * (exp(-db * db * 0.30) * 0.50 + exp(-db * db * 0.020) * 0.22 + exp(-db * db * 0.002) * 0.05);
    }
    return col * act;
}

float terrH(float x, float z) {
    float t = uT.x;
    float zf = z + t * 0.05;
    float sw = sin(x * 19.0 + zf * 7.0 + t * 0.30) * 0.9
        + 0.6 * sin(x * 33.0 - zf * 11.0 - t * 0.42)
        + 0.35 * sin(x * 54.0 + zf * 17.0);
    return sw * (0.45 + 0.9 * uAux.y + 0.5 * uAux.x);
}

// MIDDLE: dotted terrain rows (the grainy lavender hills)
float3 terrain(float x, float y, float bv, float pxy) {
    float t = uT.x;
    float cw = uRes.y * 0.030;
    float ci = floor(x * uRes.x / cw);
    float3 acc = float3(0.0);
    for (int k = 0; k < 12; k++) {
      if (uQ > 0.75 || mod(float(k), 2.0) < 0.5) {
        float z = (float(k) + 0.5) / 12.0;
        float zz = z * z;
        float h = terrH(x, z);
        float yk = -0.14 - 0.80 * zz + h * (0.030 + 0.070 * z) + bv * 0.10 * z;
        float hh = clamp(h * 0.28 + 0.45, 0.0, 1.0);
        float lit = 0.40 + 1.5 * hh * hh;
        float fade = (1.0 - 0.20 * z) * smoothstep(0.0, 0.10, z + 0.06) * (1.0 - smoothstep(0.86, 1.08, z)) * (1.0 + 0.7 * (1.0 - uQ));
        float3 tc = mix(float3(0.42, 0.52, 1.0), float3(0.92, 0.95, 1.0), clamp(hh * 1.3, 0.0, 1.0));
        float s = 0.0;
        float nearRow = step(abs(y - yk), 0.020 + 6.0 * pxy);
        for (int j = 0; j < 2; j++) {
          if (nearRow > 0.5) {
            float2 id = float2(ci + float(k) * 41.0, float(j) + 71.0);
            float h1 = hash(id);
            float h2 = hash(id + 5.1);
            float h3 = hash(id + 9.7);
            float bxn = (ci + 0.25 + 0.5 * h1) * cw / uRes.x;
            float ddx = (x - bxn) * uRes.x;
            float ddy = (y - (yk + (h2 - 0.5) * (0.012 + 0.030 * z))) / pxy;
            float r = 0.60 + 0.5 * h3 + 0.5 * z;
            float tw = 0.7 + 0.3 * sin(t * (1.5 + 4.0 * h3) + h2 * 40.0);
            s += exp(-(ddx * ddx + ddy * ddy) / (r * r) * 1.3) * (0.5 + 0.9 * h1 * h1) * tw;
          }
        }
        float dl2 = (y - yk) / pxy;
        s += exp(-dl2 * dl2 * 0.30) * 0.07 * (0.5 + hh);
        acc += tc * s * lit * fade;
      }
    }
    return acc;
}

"""
}
