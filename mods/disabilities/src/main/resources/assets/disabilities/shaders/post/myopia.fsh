#version 330
#extension GL_ARB_separate_shader_objects : require

// Severe myopia: sharp up close, more and more blurred with distance (one direction per pass).
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
};

layout(std140) uniform MyopiaConfig {
    vec2 BlurDir;
    float Strength;
};

layout(location = 0) out vec4 fragColor;

// Depth is reversed (1 = near plane at 0.05 blocks, 0 = infinitely far): distance is about near / depth.
float distanceInBlocks(float depth) {
    return 0.05 / max(depth, 0.000001);
}

void main() {
    float dist = distanceInBlocks(texture(DepthSampler, texCoord).r);
    float radius = smoothstep(2.5, 9.0, dist) * 9.0 * Strength;
    if (radius < 0.5) {
        fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
        return;
    }
    vec2 pixel = BlurDir / OutSize;
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    for (int i = -10; i <= 10; i++) {
        float f = float(i) / 10.0;
        float w = exp(-3.0 * f * f);
        sum += texture(InSampler, texCoord + pixel * f * radius).rgb * w;
        weights += w;
    }
    fragColor = vec4(sum / weights, 1.0);
}
