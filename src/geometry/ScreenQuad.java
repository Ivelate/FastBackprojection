package geometry;
import static org.lwjgl.opengl.GL15.glGenBuffers;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.Util;
import org.lwjgl.util.vector.Matrix4f;

import ivengine.MatrixHelper;
import ivengine.shaders.SimpleShaderProgram;

import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL11.glDrawArrays;

/**
 * Full screen quad.
 */
public class ScreenQuad 
{
	private int vbo;
	public ScreenQuad()
	{
		this.vbo=glGenBuffers();
		glBindBuffer(GL15.GL_ARRAY_BUFFER,this.vbo);
		FloatBuffer fbuff=BufferUtils.createFloatBuffer(2*6);
		float[] coordPoints=new float[]{
				-1,-1,
				1,-1,
				-1,1,
				1,-1,
				1,1,
				-1,1
		};
		fbuff.put(coordPoints);
		fbuff.flip();
		glBufferData(GL15.GL_ARRAY_BUFFER,fbuff,GL15.GL_STATIC_DRAW);
		glBindBuffer(GL15.GL_ARRAY_BUFFER,0);
	}
	public void draw(SimpleShaderProgram SSP)
	{
		glBindBuffer(GL15.GL_ARRAY_BUFFER,this.vbo);
		SSP.setupAttributes();
		
		glDrawArrays(GL11.GL_TRIANGLES, 0, 6); 
		glBindBuffer(GL15.GL_ARRAY_BUFFER,0); 
	}
	
	public void cleanup() {
		GL15.glDeleteBuffers(this.vbo);
	}
}
