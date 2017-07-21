package shader;

import ivengine.shaders.SimpleShaderProgram;
import org.lwjgl.opengl.GL11;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetAttribLocation;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

public class PrintingBasicShader extends SimpleShaderProgram
{
	public PrintingBasicShader()
	{
		super("/shaders/printBasicShader.vsh","/shaders/printBasicShader.fsh",true);
	}

	@Override
	public void setupAttributes() 
	{
		int locAttrib=glGetAttribLocation(this.getID(),"location");
		glVertexAttribPointer(locAttrib,3,GL11.GL_FLOAT,false,4*this.getSize(),0);
		glEnableVertexAttribArray(locAttrib);
		int normalAttrib=glGetAttribLocation(this.getID(),"normal");
		glVertexAttribPointer(normalAttrib,1,GL11.GL_FLOAT,false,4*this.getSize(),4*3);
		glEnableVertexAttribArray(normalAttrib);
	}

	@Override
	public int getSize() 
	{
		return 4;
	}

	@Override
	protected void dispose() 
	{

	}
}
