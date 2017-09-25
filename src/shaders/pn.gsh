#version 440 core

uniform mat4 viewProj;

layout(triangles) in;
layout (triangle_strip, max_vertices=3) out;

out VertexData {
    vec3 Normal;
    flat int dominantAxis;
    flat float intensity;
} VertexOut;

in VData {
	flat float intensityy;
} VertexInn[3];

/*const mat4 swizzlers[3] =  mat4[3](
                     mat4( vec4(1,0,0,0) , vec4(0,1,0,0) , vec4(0,0,1,0) , vec4(0,0,0,1)),
                     mat4( vec4(1,0,0,0) , vec4(0,0,1,0) , vec4(0,1,0,0) , vec4(0,0,0,1)),
                     mat4( vec4(0,0,1,0) , vec4(0,1,0,0) , vec4(1,0,0,0) , vec4(0,0,0,1))
                    );
 */
void main()
{
  vec3 normal=cross((gl_in[2].gl_Position-gl_in[1].gl_Position).xyz,(gl_in[0].gl_Position - gl_in[1].gl_Position).xyz);
  vec3 absnormal=abs(normal);
  int dominantAxis=2;
  if(absnormal.z>absnormal.y&&absnormal.z>absnormal.x) dominantAxis=0;
  else if(absnormal.y>absnormal.x) dominantAxis=1;
  for(int i = 0; i < gl_in.length(); i++)
  {
     // copy attributes
     vec4 projectedPos=viewProj * gl_in[i].gl_Position;
     gl_Position= 		dominantAxis==0?projectedPos:
     					dominantAxis==1?projectedPos.xzyw: 
     					projectedPos.zyxw;
     /*vec4 swizzledPos= 	dominantAxis==0?gl_in[i].gl_Position:
     					dominantAxis==1?gl_in[i].gl_Position.xzyw: 
     					gl_in[i].gl_Position.zyxw;
     
    gl_Position = 	viewProj * swizzledPos;*/
    VertexOut.Normal = normal;
    VertexOut.dominantAxis=dominantAxis;
    VertexOut.intensity=VertexInn[i].intensityy;
 
    // done with the vertex
    EmitVertex();
  }
}