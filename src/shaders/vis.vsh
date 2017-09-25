#version 440 core

uniform mat4 mvp;
uniform mat4 model;

in vec3 location;
in float color;

out vec3 Color;

void main()
{
	uint icolor=uint(floor(color+0.5));
	if(icolor==0) Color=vec3(1,1,1);
	if(icolor==1) Color=vec3(1,0,0);
	if(icolor==2) Color=vec3(0,1,0);
	if(icolor==3) Color=vec3(0,0,1);
	gl_Position=mvp * model *vec4(location.xyz,1);
}