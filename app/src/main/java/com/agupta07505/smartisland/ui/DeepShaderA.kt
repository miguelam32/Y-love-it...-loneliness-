package com.agupta07505.smartisland.ui

internal object DeepShaderA {
    const val SRC = """
uniform float2 uRes;
uniform float2 uT;
uniform float4 uAux;
uniform float2 uEnv[40];
uniform float uBand[24];
uniform float uBeat[40];
uniform float4 uScA[24];
uniform float4 uScB[12];
uniform float4 uImp;
uniform float3 uK;
uniform float uFrac;
uniform float uQ;

float hash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, float3(p3.y, p3.z, p3.x) + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// DEEP FIELD gradient: cyan -> blue -> violet -> magenta
float3 gradc(float u) {
    u = clamp(u, 0.0, 1.0);
    float3 p0 = float3(0.10, 0.85, 1.00);
    float3 p1 = float3(0.22, 0.50, 1.00);
    float3 p2 = float3(0.55, 0.28, 1.00);
    float3 p3 = float3(0.95, 0.35, 0.90);
    return mix(mix(p0, p1, smoothstep(0.0, 0.33, u)), mix(p2, p3, smoothstep(0.66, 1.0, u)), smoothstep(0.33, 0.66, u));
}

// x = PCM mid line, y = smoothed half amplitude, z = beat marker, w = beat halo
float4 envAt(float x) {
    float cx = x * 39.0 + uFrac;
    float c = 0.0;
    float cws = 0.0;
    float sm = 0.0;
    float ws = 0.0;
    float bm = 0.0;
    float bw = 0.0;
    for (int i = 0; i < 40; i++) {
        float d = cx - float(i);
        float ab = abs(d);
        bm = max(bm, uBeat[i] * max(0.0, 1.0 - ab * 0.8));
        bw = max(bw, uBeat[i] * max(0.0, 1.0 - ab / 16.0));
        if (ab < 7.0) {
            float g1 = exp(-d * d / 2.6);
            c += 0.5 * (uEnv[i].x + uEnv[i].y) * g1;
            cws += g1;
            float g = exp(-d * d / 12.5);
            sm += 0.5 * (uEnv[i].y - uEnv[i].x) * g;
            ws += g;
        }
    }
    return float4(c / max(cws, 0.001), sm / max(ws, 0.001), bm, bw);
}

float bandAt(float x) {
    float bx = x * 23.0;
    float v = 0.0;
    for (int i = 0; i < 24; i++) {
        v += uBand[i] * max(0.0, 1.0 - abs(bx - float(i)));
    }
    return v;
}

// real PCM waveform lines (A: 96 pts, B: 48 pts). returns (fA, dfA/dx, fB, dfB/dx), smooth (C1) interpolation
float4 scopeAt(float x) {
    float bxa = clamp(x, 0.0, 1.0) * 95.0;
    float bxb = clamp(x, 0.0, 1.0) * 47.0;
    float fa = 0.0;
    float da = 0.0;
    float fb = 0.0;
    float db = 0.0;
    for (int i = 0; i < 24; i++) {
        float k = float(i) * 4.0;
        float4 d = bxa - float4(k, k + 1.0, k + 2.0, k + 3.0);
        float4 w = max(float4(0.0), 1.0 - abs(d));
        float4 sw = w * w * (3.0 - 2.0 * w);
        float4 dw = 6.0 * w * (1.0 - w) * (-sign(d));
        fa += dot(uScA[i], sw);
        da += dot(uScA[i], dw);
    }
    for (int i = 0; i < 12; i++) {
        float k = float(i) * 4.0;
        float4 d = bxb - float4(k, k + 1.0, k + 2.0, k + 3.0);
        float4 w = max(float4(0.0), 1.0 - abs(d));
        float4 sw = w * w * (3.0 - 2.0 * w);
        float4 dw = 6.0 * w * (1.0 - w) * (-sign(d));
        fb += dot(uScB[i], sw);
        db += dot(uScB[i], dw);
    }
    return float4(fa, da * 95.0, fb, db * 47.0);
}

// expanding light ring born at every detected beat (the "golpe de luz")
float3 bursts(float x, float y, float yc) {
    float acc = 0.0;
    float core = 0.0;
    float k = uRes.x / (uRes.y * 0.5);
    for (int i = 0; i < 40; i++) {
        float b = uBeat[i];
        if (b > 0.3) {
            float a = (39.0 - float(i)) / 39.0;
            float xi = (float(i) - uFrac) / 39.0;
            float dx = (x - xi) * k;
            float dy = (y - yc) * 1.25;
            float d = sqrt(dx * dx + dy * dy);
            float rr = 0.10 + a * (1.1 + 1.4 * b);
            float w = 0.05 + 0.16 * a;
            float fa = (1.0 - a) * (1.0 - a);
            acc += b * fa * exp(-(d - rr) * (d - rr) / (w * w));
            core += b * fa * fa * exp(-d * d * 14.0);
        }
    }
    return float3(acc, core, 0.0);
}

"""
}
