package com.agupta07505.smartisland.ui

internal object DeepShaderA {
    const val SRC = """
uniform float2 uRes;
uniform float2 uT;
uniform float4 uAux;
uniform float2 uEnv[40];
uniform float uBand[24];
uniform float uBeat[40];
uniform float4 uImp;
uniform float3 uK;

float3 pal(float t) {
    t = 1.0 - abs(fract(t * 0.5) * 2.0 - 1.0);
    float3 a = float3(0.10, 0.95, 1.00);
    float3 b = float3(0.22, 0.42, 1.00);
    float3 c = float3(0.68, 0.28, 1.00);
    float3 d = float3(1.00, 0.22, 0.78);
    float3 r = mix(a, b, smoothstep(0.0, 0.34, t));
    r = mix(r, c, smoothstep(0.30, 0.64, t));
    r = mix(r, d, smoothstep(0.60, 0.95, t));
    return r;
}

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float3 ribbon(float x, float y) {
    float lo = 0.0;
    float hi = 0.0;
    float bm = 0.0;
    float cx = x * 39.0;
    for (int i = 0; i < 40; i++) {
        float w = max(0.0, 1.0 - abs(cx - float(i)));
        lo += uEnv[i].x * w;
        hi += uEnv[i].y * w;
        bm = max(bm, uBeat[i] * max(0.0, 1.0 - abs(cx - float(i)) * 0.8));
    }
    float taper = pow(clamp(sin(3.14159 * clamp(x, 0.0, 1.0)), 0.0, 1.0), 0.6);
    float c = 0.5 * (lo + hi) * 0.55;
    float h = max(0.5 * (hi - lo), 0.05) * 0.78 * taper;
    float px = 1.0 / uRes.y;
    float3 acc = float3(0.0);
    for (int k = 0; k < 7; k++) {
        float fk = float(k);
        float t = fk / 6.0 * 2.0 - 1.0;
        float rip = sin(x * 24.0 + uT.x * 3.4 + fk * 1.7) * 0.10 * (0.25 + uAux.y * 1.2);
        float yk = c * taper + t * h * (1.0 + rip * 2.0) + rip * 0.12 * taper;
        float d = (y - yk) / (px * 2.0);
        float core = exp(-d * d * 1.1);
        float glow = exp(-d * d * 0.05) * 0.22;
        float3 col = pal(x * 0.55 + (t * 0.5 + 0.5) * 0.35 + uT.x * 0.04 + uAux.x * 0.15);
        float outer = step(0.99, abs(t));
        float dxp = (fract(x * 13.0 + 0.5) - 0.5) * uRes.x / 13.0;
        float node = exp(-(dxp * dxp + d * d) * 0.8) * outer * 0.9;
        acc += col * (core * 0.95 + glow) + float3(0.8, 0.95, 1.0) * node;
    }
    float span = h + 0.25;
    float vb = smoothstep(span, span * 0.5, abs(y - c * taper));
    float mk = bm * bm * vb;
    acc += float3(1.0, 0.92, 0.75) * mk * 0.9;
    float dm = (y - c * taper) / (px * 2.0);
    acc += float3(1.0, 1.0, 1.0) * bm * exp(-dm * dm * 0.35) * 0.8;
    return acc;
}

float3 scene(float2 uv, float off) {
    float x = uv.x;
    float y = (0.5 - uv.y) * 2.0;
    float beat = uAux.w;
    float gl = step(0.5, beat) * beat;
    float sl = floor(uv.y * 9.0);
    float xg = x + (hash(float2(sl, floor(uT.x * 24.0))) - 0.5) * 0.07 * gl;
    float3 rc = ribbon(xg + off, y);
    float3 gc = ribbon(xg, y);
    float3 bc = ribbon(xg - off, y);
    float3 col = float3(rc.x, gc.y, bc.z);

    float bandV = 0.0;
    float bx = x * 23.0;
    for (int i = 0; i < 24; i++) {
        float w = max(0.0, 1.0 - abs(bx - float(i)));
        bandV += uBand[i] * w;
    }
    float fromBottom = (1.0 - uv.y);
    float cell = abs(fract(x * 24.0) - 0.5);
    float barMask = smoothstep(0.5, 0.30, cell);
    float bh = bandV * 0.55;
    float inside = smoothstep(bh, bh - 0.10, fromBottom);
    float3 bcol = pal(x * 0.6 + 0.1 + fromBottom * 0.25);
    col += bcol * inside * barMask * (0.12 + 0.18 * (1.0 - fromBottom / max(bh, 0.05)));

    float vg = fromBottom + 0.12;
    float gx = abs(fract((x - 0.5) / vg * 1.6 + 0.5) - 0.5);
    float gy = abs(fract(1.0 / vg * 0.7 - uT.x * (0.5 + uAux.x)) - 0.5);
    float grid = (smoothstep(0.05, 0.0, gx * vg) + smoothstep(0.06, 0.0, gy)) * smoothstep(0.42, 0.0, fromBottom);
    col += pal(x * 0.5 + 0.55) * grid * (0.10 + uAux.x * 0.25);
    col *= 0.93 + 0.07 * sin(uv.y * uRes.y * 3.14159);

    float2 gp = floor(float2(uv.x * 64.0, uv.y * 36.0));
    float hs = hash(gp);
    float tw = 0.5 + 0.5 * sin(uT.x * (2.0 + hs * 5.0) + hs * 40.0);
    float near = exp(-pow(y * 1.4, 2.0) * 2.0);
    float glit = step(0.965, hs) * tw * near * (0.5 + beat * 2.2);
    col += pal(hs * 3.0 + x) * glit;

    col += pal(x * 0.5 + uT.x * 0.05) * (beat * beat) * 0.22 * exp(-y * y * 1.5);

    return col;
}

"""
}
