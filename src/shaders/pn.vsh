#version 450 core

in vec3 location;
in mat4 model;
in float intensity;

out VertexData {
	float intensity;
} VertexOut;

void main()
{
	VertexOut.intensity=intensity;
	gl_Position=model * vec4(location.xyz,1);
}