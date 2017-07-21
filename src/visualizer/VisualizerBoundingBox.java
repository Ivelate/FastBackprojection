package visualizer;

import static org.lwjgl.opengl.GL15.glBindBuffer;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import ivengine.MatrixHelper;
import shader.VisualizationShader;

public class VisualizerBoundingBox 
{
	private int vbo=-1;
	public VisualizerBoundingBox(int size) //Supposing size cubic
	{
		this.vbo=GL15.glGenBuffers();
		FloatBuffer fbuff=BufferUtils.createFloatBuffer(4*12*2);
		float[] lines={	0,0,0,0,				size,0,0,0,
						0,0,0,0, 				0,size,0,0,
						0,0,0,3, 				0,0,size,3,
						0,size,0,0, 			size,size,0,0,
						0,size,0,0, 			0,size,size,0,
						size,0,0,0, 			size,size,0,0,
						size,0,0,0,				size,0,size,0,
						0,0,size,1, 			size,0,size,1,
						0,0,size,2,				0,size,size,2,
						size,size,size,0, 		0,size,size,0,
						size,size,size,0, 		size,0,size,0,
						size,size,size,0, 		size,size,0,0
		};
		fbuff.put(lines);
		fbuff.flip();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.vbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fbuff,GL15.GL_STATIC_DRAW);
	}
	public void render(Matrix4f projView,Matrix4f model,VisualizationShader vs)
	{
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.vbo);
		vs.enable();
		vs.setupAttributes();
		MatrixHelper.uploadMatrix(projView, GL20.glGetUniformLocation(vs.getID(), "mvp"));
		MatrixHelper.uploadMatrix(model , GL20.glGetUniformLocation(vs.getID(), "model"));
		GL11.glDrawArrays(GL11.GL_LINES, 0, 12*2);
		vs.disable();
		glBindBuffer(GL15.GL_ARRAY_BUFFER,0);
	}
}
