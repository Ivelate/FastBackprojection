#version 440 core

in vec3 location;
in mat4 model;
in float intensity;

out VData {
	flat float intensityy;
} VertexOutt;

void main()
{
	VertexOutt.intensityy=intensity;
	gl_Position=model * vec4(location.xyz,1);
}