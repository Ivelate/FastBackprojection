package geometry;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;
import org.lwjgl.util.vector.Matrix4f;

import ivengine.MatrixHelper;
import ivengine.shaders.SimpleShaderProgram;

import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL11.glDrawArrays;

/**
 * Each instance of this class contains all vertex data for a given recursion level of a origin center, one radius sphere approximation.
 */
//|TODO Don't work if more than Integer.MAX_VALUE/64 ellipsoids are used. Why would somebody do that!? Anyways, fixmeplz
public class Sphere 
{
	//Precalculated sphere approximation errors, up to recursion 9
	public static final float[] ICOSAHEDRON_RECURSION_ERRORS={0.20534551745326302f,0.055975727752992754f,0.014293541751767114f,0.0035922377132593386f,8.992721907717938E-4f,2.248539997533605E-4f,5.626836773253707E-5f,1.412640058939818E-5f,3.427272947664761E-6f,8.046630282088074E-7f};
	private final int SPHERE_RECURSIONS;
	
	private final int SPHERE_TRIANGLES;
	private final boolean EXTEND_HALF; //If true, semi sphere. If false, full sphere. Full sphere is only needed when the wall doesn't share a common normal.
	
	private int currentTriangles=0;
	private int vbo;
	
	private int ellipsoidModelMatrixVbo=-1;
	private int ellipsoidIntensitiesVbo=-1;
	private int ellipsoidNumber=0;
	
	private boolean VERBOSE;
	
	private ArrayList<Matrix4f> ellipsoidMatrixes=new ArrayList<Matrix4f>(); //EDIT @date 10/01/2017 
	private ArrayList<Byte> ellipsoidIntensities=new ArrayList<Byte>();      //now using ArrayList instead of LinkedList - much more memory efficient
	
	public Sphere(int recursions,boolean VERBOSE,boolean halfSphere)
	{
		SPHERE_RECURSIONS=recursions;
		EXTEND_HALF=halfSphere;
		SPHERE_TRIANGLES=(int)(Math.pow(4, SPHERE_RECURSIONS))*3*20*(halfSphere?2:1);
		this.VERBOSE=VERBOSE;
	}
	
	public void initBuffers(FloatBuffer fbuff,ByteBuffer bbuff)
	{
		if(this.ellipsoidMatrixes.size()==0) return;
		
		this.vbo=glGenBuffers();
		glBindBuffer(GL15.GL_ARRAY_BUFFER,this.vbo);
		FloatBuffer sphbuff=createSphere();

		glBufferData(GL15.GL_ARRAY_BUFFER,sphbuff,GL15.GL_STATIC_DRAW);
		glBindBuffer(GL15.GL_ARRAY_BUFFER,0);
		
		this.ellipsoidModelMatrixVbo=glGenBuffers();
		this.ellipsoidIntensitiesVbo=glGenBuffers();
		
		glBindBuffer(GL15.GL_ARRAY_BUFFER,this.ellipsoidIntensitiesVbo);
		glBufferData(GL15.GL_ARRAY_BUFFER,ellipsoidIntensities.size(),GL15.GL_STATIC_DRAW);
		glBindBuffer(GL15.GL_ARRAY_BUFFER,this.ellipsoidModelMatrixVbo);
		glBufferData(GL15.GL_ARRAY_BUFFER,ellipsoidMatrixes.size()*16*4,GL15.GL_STATIC_DRAW);
		
		//Insert the matrixes
		int lastUploaded=0;
		int counter=0;
		for(int i=0;i<ellipsoidMatrixes.size();i++)
		{
			Matrix4f aux=ellipsoidMatrixes.get(i);
			byte intensity=ellipsoidIntensities.get(i);
			aux.store(fbuff);
			bbuff.put(intensity);
			counter++;
			if(!fbuff.hasRemaining()){
				fbuff.flip();
				bbuff.flip();
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,lastUploaded*4*16,fbuff);
				glBindBuffer(GL15.GL_ARRAY_BUFFER,this.ellipsoidIntensitiesVbo);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,lastUploaded,bbuff);
				glBindBuffer(GL15.GL_ARRAY_BUFFER,this.ellipsoidModelMatrixVbo);
				lastUploaded=counter;
				
			}
		}
		this.ellipsoidMatrixes=new ArrayList<Matrix4f>(); //Deallocating
		this.ellipsoidIntensities=new ArrayList<Byte>(); //Deallocating
		if(counter>lastUploaded){
			fbuff.flip();
			bbuff.flip();
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,lastUploaded*4*16,fbuff);
			glBindBuffer(GL15.GL_ARRAY_BUFFER,this.ellipsoidIntensitiesVbo);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,lastUploaded,bbuff);
		}
		if(VERBOSE)System.out.println("Rec "+this.SPHERE_RECURSIONS+", Uploaded "+counter+" ellipsoid matrixes succesfully, "+(counter*SPHERE_TRIANGLES)+" triangles");

		glBindBuffer(GL15.GL_ARRAY_BUFFER,0);
	}
	public int getEllipsoidsVbo()
	{
		return this.ellipsoidModelMatrixVbo;
	}
	public int getIntensitiesVbo()
	{
		return this.ellipsoidIntensitiesVbo;
	}
	public int getEllipsoidNumber()
	{
		return this.ellipsoidNumber;
	}
	public void putEllipsoid(Matrix4f ellipsoid,byte intensity)
	{
		this.ellipsoidIntensities.add(intensity);
		this.ellipsoidMatrixes.add(ellipsoid);
		this.ellipsoidNumber++;
	}
	public void draw(SimpleShaderProgram SSP,int modelMatrixLoc,int intensitiesLoc,int DELAYED_RENDER_BATCH_SIZE/*,float percentEllipsoidsDraw*/)
	{
		draw(SSP,modelMatrixLoc,intensitiesLoc,DELAYED_RENDER_BATCH_SIZE,this.ellipsoidNumber,0);
	}
	public void draw(SimpleShaderProgram SSP,int modelMatrixLoc,int intensitiesLoc,int DELAYED_RENDER_BATCH_SIZE,int amount,int offset/*,float percentEllipsoidsDraw*/)
	{
		if(this.ellipsoidModelMatrixVbo==-1) return;
		
		glBindBuffer(GL15.GL_ARRAY_BUFFER,this.ellipsoidModelMatrixVbo);
		
		for(int i=0;i<4;i++) {
			glVertexAttribPointer(modelMatrixLoc+i,4,GL11.GL_FLOAT,false,4*16,4*4*i);
			glEnableVertexAttribArray(modelMatrixLoc+i);
			GL33.glVertexAttribDivisor( modelMatrixLoc+i, 1);
		}
		
		glBindBuffer(GL15.GL_ARRAY_BUFFER,this.ellipsoidIntensitiesVbo);
		
		GL20.glVertexAttribPointer(intensitiesLoc,1,GL11.GL_UNSIGNED_BYTE ,false,1,0);
		glEnableVertexAttribArray(intensitiesLoc);
		GL33.glVertexAttribDivisor(intensitiesLoc, 1);

		glBindBuffer(GL15.GL_ARRAY_BUFFER,this.vbo);
		SSP.setupAttributes();
		
		/*Matrix4f identity=new Matrix4f();
		MatrixHelper.uploadMatrix(identity, GL20.glGetUniformLocation(SSP.getID(),"model"));*/
		
		if(DELAYED_RENDER_BATCH_SIZE>0)
		{
			for(int i=offset;i<(int)(amount+offset);i+=DELAYED_RENDER_BATCH_SIZE)
			{
				int currentDrawSize=Math.min(amount+offset-i,DELAYED_RENDER_BATCH_SIZE);
				GL42.glDrawArraysInstancedBaseInstance(GL11.GL_TRIANGLES, 0, this.currentTriangles*3, currentDrawSize, i);
				GL11.glFinish();
				if(VERBOSE)System.out.println("(Rec "+SPHERE_RECURSIONS+") - Rendered "+i+" / "+this.ellipsoidNumber);
			}
		}
		else GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, this.currentTriangles*3,this.ellipsoidNumber);
		//glDrawArrays(GL11.GL_TRIANGLES, 0, SPHERE_TRIANGLES);
		glBindBuffer(GL15.GL_ARRAY_BUFFER,0);
	}
	private FloatBuffer createSphere()
	{
		FloatBuffer fbuf=BufferUtils.createFloatBuffer(GuiUtils.SHADER_ARGS*SPHERE_TRIANGLES);

		this.currentTriangles=GuiUtils.drawSphere(0, 0, 0, 1, SPHERE_RECURSIONS, fbuf,EXTEND_HALF);
		
		fbuf.flip();
		return fbuf;
	}
	
	public int getTrianglesPerSphere()
	{
		return this.currentTriangles;
	}

	public void cleanup() {
		if(this.ellipsoidIntensitiesVbo!=-1) GL15.glDeleteBuffers(this.ellipsoidIntensitiesVbo);
		if(this.ellipsoidModelMatrixVbo!=-1) GL15.glDeleteBuffers(this.ellipsoidModelMatrixVbo);
		if(this.vbo!=-1) GL15.glDeleteBuffers(this.vbo);
	}
	
}
