#version 440 core

const vec3 NormalTable[6]=vec3[](
							vec3(0,0,1),
							vec3(0,0,-1),
							vec3(-1,0,0),
							vec3(1,0,0),
							vec3(0,-1,0),
							vec3(0,1,0)
							);
							
uniform mat4 mvp;
uniform mat4 model;

in vec3 location;
in float normal;

out vec3 Normal;

void main()
{
	int normalInd=int(normal+0.5);
	Normal=normalize((model * vec4(NormalTable[normalInd].xyz,0)).xyz);
	gl_Position=mvp * model * vec4(location.xyz,1);
}