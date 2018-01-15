#version 330

uniform vec4 emission;
uniform vec4 ambient;
uniform vec4 diffuse;

uniform int lightCount;
uniform vec3 lightPosition[4];
uniform vec4 lightColor[4];
uniform bool lightIsDirectional[4];

in vec3 vEyeCoord;
in vec3 vNormal;

out vec4 fragColor;

vec3 lightDirection(int i) {
    if (lightIsDirectional[i]) {
        return lightPosition[i];
    }
    return lightPosition[i] - vEyeCoord;
}

void main() {
    vec3 lightVector[4];
    for (int i = 0; i < lightCount; i++) {
        lightVector[i] = normalize(lightDirection(i));
    }

    vec3 nNormal = normalize(vNormal);

	fragColor = vec4(0);

    fragColor += emission;

    for (int i = 0; i < lightCount; i++) {
        fragColor += ambient * lightColor[i];
    }

    for (int i = 0; i < lightCount; i++) {
        fragColor += diffuse * lightColor[i] * max(dot(nNormal, lightVector[i]), 0);
    }
}
