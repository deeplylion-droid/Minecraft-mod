#version 150

// Sotto la Bedrock: the faded look (washed-out colours, lifted blacks, grain, vignette) and the
// glitches (torn rows, shifted blocks, split colour channels, negative, blackout).

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Time;
uniform float Fade;
uniform float Glitch;
uniform float Seed;
uniform float Black;
uniform float Invert;
uniform float Grain;
uniform float Vignette;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;

    if (Glitch > 0.0) {
        // whole rows torn sideways
        float band = floor(uv.y * 28.0 + Seed * 13.0);
        if (hash(vec2(band, Seed)) < Glitch * 0.55) {
            uv.x += (hash(vec2(band, Seed + 1.7)) - 0.5) * 0.22 * Glitch;
        }
        // a few blocks of the picture shifted
        vec2 cell = floor(uv * vec2(18.0, 10.0));
        if (hash(cell + vec2(Seed, Seed * 0.37)) < Glitch * 0.12) {
            uv += (vec2(hash(cell + Seed * 2.1), hash(cell + Seed * 3.3)) - 0.5) * 0.12;
        }
        uv = clamp(uv, oneTexel, 1.0 - oneTexel);
    }

    float split = Glitch * 0.014;
    vec3 col;
    col.r = texture(DiffuseSampler, uv + vec2(split, 0.0)).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, uv - vec2(split, 0.0)).b;

    // faded: most of the colour gone, blacks lifted, whites dimmed, a slightly warm old-photo tint
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 faded = mix(col, vec3(luma), 0.8);
    faded *= vec3(1.03, 1.0, 0.93);
    faded = 0.06 + faded * 0.84;
    col = mix(col, faded, Fade);

    // vignette
    vec2 d = texCoord - 0.5;
    col *= 1.0 - clamp(dot(d, d) * 2.2 * Vignette, 0.0, 0.85);

    // grain, and static when it glitches hard
    float noise = hash(floor(texCoord * InSize) + vec2(Time * 977.0, Seed * 131.0)) - 0.5;
    col += noise * (Grain + 0.45 * Glitch * Glitch);

    col = mix(col, vec3(1.0) - col, Invert);
    col *= 1.0 - Black;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
