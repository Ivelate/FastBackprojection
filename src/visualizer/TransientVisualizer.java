package visualizer;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL15.glBindBuffer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import core.TransientVoxelization;
import ivengine.Camera;
import ivengine.IvEngine;
import ivengine.MatrixHelper;
import shader.PrintingBasicShader;
import shader.VisualizationShader;
import visualizer.gui.TransientVoxelizationGuiFrame;


/*import ucar.nc2.Dimension;
import ucar.ma2.*;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;*/

public class TransientVisualizer 
{	
	private static final boolean FACE_CULLING=true;
	private static final int RES=512;
	private static final double DEFAULT_MOUSE_SENSIVITY=15;
	
	
	private VisualizerBoundingBox boundingBox;
	
	private int vbo;
	private FloatBuffer currentBuffer=null;
	private int cubesNum=0;
	private int facesNum=0;
	private boolean[][][] dataset=null;
	private float[][][] norm_data=null;
	
	private float currentThreshold=0.5f;
	private boolean redrawRequested=false;
	private boolean autoRedrawEnabled=false;
	private float currentScale=0.02f;
	private float rotspd=1;
	private int screenRotationState=0;
	
	private PrintingBasicShader PBS;
	private VisualizationShader VS;
	private Camera camera;
	
	private float cuberot=0;
	private float pitch=0;
	private float yaw=0;
	
	private TimeManager TM=new TimeManager();
	
	public TransientVisualizer(TransientVisualizerParams params) throws LWJGLException
	{
		IvEngine.configDisplay(RES, RES, "Transient Visualizer", false, false, false);
		
		this.currentThreshold=params.initialThreshold;
		this.screenRotationState=params.initialScreenRotationState;
		initResources(params);
		
		boolean run=true;
		//Mouse.setGrabbed(true);
		//Flush TM before starting 
		TM.getDeltaTime();
		while(!Display.isCloseRequested()&&run)
		{
			if(Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) || params.clearAtFirstRender) run=false;

			update(TM.getDeltaTime());
			render();
			Display.sync(60);
			Display.update();
		}
		
		cleanup();
	}
	private void update(float tEl)
	{
		while(Keyboard.next()) handleKeyboardInput();
		
		/*this.pitch+=(-(float)((Mouse.getDY())/DEFAULT_MOUSE_SENSIVITY))*tEl;
		if(this.pitch>Math.PI/2) this.pitch=(float)Math.PI/2;
		else if(this.pitch<-Math.PI/2) this.pitch=-(float)Math.PI/2;
		this.yaw+=((float)((Mouse.getDX())/DEFAULT_MOUSE_SENSIVITY))*tEl;*/
		this.cuberot+=tEl*this.rotspd;
		
		int dwheel=Mouse.getDWheel();
		if(dwheel>0) this.currentScale*=1.5f;
		else if(dwheel<0) this.currentScale/=1.5f;
		
		if(dwheel!=0) System.out.println("Scale change: "+this.currentScale);
		
		this.camera.setPitch(this.pitch);
		this.camera.setYaw(this.yaw);
		
		if(norm_data!=null)
		{
			if(this.redrawRequested){
				this.redrawRequested=false;
				System.out.println("Redrawing...");
				this.cubesNum=this.applyThreshold(this.dataset, this.currentThreshold);
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
				this.currentBuffer=genDataChunk(this.dataset,this.cubesNum);
				GL15.glBufferData(GL15.GL_ARRAY_BUFFER,this.facesNum*6*4*4,this.norm_data==null?GL15.GL_STATIC_DRAW:GL15.GL_DYNAMIC_DRAW);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, this.currentBuffer);
			}
		}
	}
	private void render()
	{
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		
		glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
		PBS.enable();
		PBS.setupAttributes();
		MatrixHelper.uploadMatrix(this.camera.getProjectionViewMatrix(), GL20.glGetUniformLocation(PBS.getID(), "mvp"));
		Matrix4f model=new Matrix4f();
		Matrix4f.scale(new Vector3f(this.currentScale,this.currentScale,this.currentScale), model,model);
		Matrix4f.rotate(this.cuberot, new Vector3f(0,1,0), model, model);
		Matrix4f.translate(new Vector3f(-this.dataset.length/2f,-this.dataset[0].length/2f,-this.dataset[0][0].length/2f),model,model);
		MatrixHelper.uploadMatrix(model , GL20.glGetUniformLocation(PBS.getID(), "model"));
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.facesNum*6);
		PBS.disable();
		glBindBuffer(GL15.GL_ARRAY_BUFFER,0);
		this.boundingBox.render(this.camera.getProjectionViewMatrix(), model, VS);
	}
	private void handleKeyboardInput()
	{
		boolean softMult=Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
		boolean ultraSoftMult=Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
		float multiplier=ultraSoftMult?0.001f:softMult?0.01f:0.05f;
		//Do things only on key down
		if(Keyboard.getEventKeyState())
		{
			switch(Keyboard.getEventKey())
			{
			case Keyboard.KEY_M:
				this.currentThreshold+=multiplier;
				if(autoRedrawEnabled) this.redrawRequested=true;
				System.out.println("Threshold change: "+this.currentThreshold);
				break;
			case Keyboard.KEY_N:
				if(this.currentThreshold-multiplier<0) System.out.println("Can't go lower: Threshold has to remain avobe 0. Use another multiplier");
				else {
					this.currentThreshold-=multiplier;
					if(autoRedrawEnabled) this.redrawRequested=true;
					System.out.println("Threshold change: "+this.currentThreshold);
				}
				break;
			case Keyboard.KEY_SPACE:
				if(ultraSoftMult){
					if(this.rotspd<0.001f) this.rotspd=softMult?0.2f:1;
					else this.rotspd=0;
				}
				else
				{
					this.redrawRequested=true;
					if(softMult){
						this.autoRedrawEnabled=!this.autoRedrawEnabled;
						System.out.println("AUTO REDRAW SET TO "+this.autoRedrawEnabled);
					}
				}
				break;
			case Keyboard.KEY_I:
				if(softMult)
				{
					this.screenRotationState++;
					if(this.screenRotationState>3) this.screenRotationState=0;
				}
				else{
					this.screenRotationState=this.screenRotationState==0?this.screenRotationState=2:0;
				}
				this.redrawRequested=true;
				System.out.println("Screen status: "+(
						this.screenRotationState==0?"Not Inverted":
						this.screenRotationState==1?"Rolled 90º":
						this.screenRotationState==2?"Inverted":
						"Rolled 270º"));

				break;
			case Keyboard.KEY_P:
				System.out.println("Printing to dumptest");
				File dumpTest=new File("dumpTest");
				dumpTest.mkdir();
				this.saveCurrentCloudToFiles(dumpTest);
				break;
			}
		}
	}
	private void cleanup()
	{
		Display.destroy();
	}
	private void initResources(TransientVisualizerParams params)
	{
		try{
			//this.dataset=loadDataset(new File("SPAR_raw_dotcloud.dcloud"));
			this.norm_data=params.overrideData==null?loadDataset(new File(params.route)):params.overrideData;

			this.dataset=new boolean[norm_data.length][norm_data.length][norm_data.length];
			this.cubesNum=this.applyThreshold(this.dataset, this.currentThreshold);
			System.out.println("Model loaded. Res: "+norm_data.length);
			System.out.println("Threshold: "+this.currentThreshold);
			System.out.println("Scale: "+this.currentScale);
		}catch(IOException e){
			System.err.println("Something exploded :(");
			e.printStackTrace();
			System.exit(0);
		}
		GL11.glClearColor(0,0,0,0);
		GL11.glViewport(0, 0, RES, RES);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable( GL11.GL_BLEND );
		GL11.glEnable(GL_TEXTURE_3D);
		GL11.glEnable(GL_TEXTURE_2D);
		vbo=GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
		this.currentBuffer=genDataChunk(this.dataset,this.cubesNum);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER,this.facesNum*6*4*4,this.norm_data==null?GL15.GL_STATIC_DRAW:GL15.GL_DYNAMIC_DRAW);
		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, this.currentBuffer);
		PBS=new PrintingBasicShader();
		VS=new VisualizationShader();
		this.camera=new Camera(0.1f,1000,90,1);
		this.camera.moveTo(0, 0, 5);
		this.camera.setYaw(0);
		
		this.boundingBox=new VisualizerBoundingBox(norm_data.length);
	}
	private boolean[][][] loadVoxelCloud(File file) throws IOException
	{
		DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
		
		int resolution=in.readInt();
		
		boolean[][][] dataset=new boolean[resolution][resolution][resolution];
		this.cubesNum=0;
		
		for(int x=0;x<dataset[0][0].length;x++)
		{
			for(int y=0;y<dataset[0].length;y++)
			{
				for(int z=0;z<dataset.length;z++)
				{
					dataset[x][y][z]=in.readBoolean();
					if(dataset[x][y][z]) this.cubesNum++;
				}
			}
		}
		
		in.close();

		return dataset;
		/*this.cubesNum=1;
		this.dataset=new boolean[1][1][1];
		dataset[0][0][0]=true;
		return dataset;*/
	}
	private float[][][] loadDataset(File file) throws IOException
	{
		DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
		
		int resolution=in.readInt();
		System.out.println(resolution);
		boolean asDouble=true;
		if(resolution<0){
			asDouble=false;
			resolution=-resolution;
		}
		
		float[][][] dataset=new float[resolution][resolution][resolution];
		this.cubesNum=0;
		
		for(int x=0;x<dataset[0][0].length;x++)
		{
			for(int y=0;y<dataset[0].length;y++)
			{
				for(int z=0;z<dataset.length;z++)
				{
					dataset[x][y][z]=asDouble?(float)in.readDouble():in.readFloat();
				}
			}
		}
		
		in.close();

		return dataset;
	}
	private int applyThreshold(boolean[][][] obj,float threshold)
	{
		final int resolution=norm_data.length;
		int cubesNum=0;
		boolean invertScreen=this.screenRotationState>=2;
		boolean swapCoords=this.screenRotationState%2==1;
		for(int zi=0;zi<resolution;zi++)
		{
			for(int yi=0;yi<resolution;yi++)
			{
				int y=invertScreen?resolution-1-yi:yi;
				int z=swapCoords?y:zi;
				if(swapCoords) y=zi;
				for(int x=0;x<resolution;x++)
				{
					boolean includeVoxel=false;
					float c=this.norm_data[x][y][z];
					if(c>threshold && x>0&&x<resolution-1 && y>0&&y<resolution-1 && z>0&&z<resolution-1)
					{
						int cont=-1;
						for(int xf=-1;xf<=1;xf++) for(int yf=-1;yf<=1;yf++) for(int zf=-1;zf<=1;zf++) if(norm_data[x+xf][y+yf][z+zf]>threshold) cont++;
						
						if(cont>=4) includeVoxel=true;
					}
					obj[x][yi][zi]=includeVoxel;
					if(includeVoxel) cubesNum++;
				}
			}
		}
		
		return cubesNum;
	}
	private FloatBuffer genDataChunk(boolean[][][] dataset,int cubesNum)
	{
		/*for(int x=0;x<dataset.length;x++)
		{
			for(int y=0;y<dataset.length;y++)
			{
				for(int z=0;z<dataset.length;z++)
				{
					dataset[x][y][z]=true;
				}
			}
		}
		cubesNum=dataset.length*dataset.length*6;*/
		int DPT=4*3;
		FloatBuffer fbuff=BufferUtils.createFloatBuffer(cubesNum*DPT*6*6);
		float[] auxBuff=new float[DPT*2];
		this.facesNum=0;
		for(int x=0;x<dataset.length;x++)
		{
			for(int y=0;y<dataset.length;y++)
			{
				for(int z=0;z<dataset.length;z++)
				{
					if(!dataset[x][y][z]) continue;
					
					if(z+1>=dataset.length||!dataset[x][y][z+1]||!FACE_CULLING){
					auxBuff[0*DPT+0]=x; 	auxBuff[0*DPT+1]=y; 	auxBuff[0*DPT+2]=z+1; 	auxBuff[0*DPT+3]=0;
					auxBuff[0*DPT+4]=x+1; 	auxBuff[0*DPT+5]=y; 	auxBuff[0*DPT+6]=z+1; 	auxBuff[0*DPT+7]=0;
					auxBuff[0*DPT+8]=x; 	auxBuff[0*DPT+9]=y+1; 	auxBuff[0*DPT+10]=z+1; 	auxBuff[0*DPT+11]=0;	
					auxBuff[1*DPT+0]=x+1; 	auxBuff[1*DPT+1]=y; 	auxBuff[1*DPT+2]=z+1; 	auxBuff[1*DPT+3]=0;
					auxBuff[1*DPT+4]=x+1; 	auxBuff[1*DPT+5]=y+1; 	auxBuff[1*DPT+6]=z+1; 	auxBuff[1*DPT+7]=0;
					auxBuff[1*DPT+8]=x; 	auxBuff[1*DPT+9]=y+1; 	auxBuff[1*DPT+10]=z+1; 	auxBuff[1*DPT+11]=0;
					fbuff.put(auxBuff);this.facesNum++;}
					
					if(z-1<0||!dataset[x][y][z-1]||!FACE_CULLING){
					auxBuff[0*DPT+0]=x;		auxBuff[0*DPT+1]=y;		auxBuff[0*DPT+2]=z;		auxBuff[0*DPT+3]=1;
					auxBuff[0*DPT+4]=x;		auxBuff[0*DPT+5]=y+1;	auxBuff[0*DPT+6]=z;		auxBuff[0*DPT+7]=1;
					auxBuff[0*DPT+8]=x+1;	auxBuff[0*DPT+9]=y;		auxBuff[0*DPT+10]=z;	auxBuff[0*DPT+11]=1;
					auxBuff[1*DPT+0]=x;		auxBuff[1*DPT+1]=y+1;	auxBuff[1*DPT+2]=z;		auxBuff[1*DPT+3]=1;
					auxBuff[1*DPT+4]=x+1;	auxBuff[1*DPT+5]=y+1;	auxBuff[1*DPT+6]=z;		auxBuff[1*DPT+7]=1;
					auxBuff[1*DPT+8]=x+1;	auxBuff[1*DPT+9]=y;		auxBuff[1*DPT+10]=z;	auxBuff[1*DPT+11]=1;
					fbuff.put(auxBuff);this.facesNum++;}
					
					if(x-1<0||!dataset[x-1][y][z]||!FACE_CULLING){
					auxBuff[0*DPT+0]=x;		auxBuff[0*DPT+1]=y;		auxBuff[0*DPT+2]=z;		auxBuff[0*DPT+3]=2;
					auxBuff[0*DPT+4]=x;		auxBuff[0*DPT+5]=y;		auxBuff[0*DPT+6]=z+1;	auxBuff[0*DPT+7]=2;
					auxBuff[0*DPT+8]=x;		auxBuff[0*DPT+9]=y+1;	auxBuff[0*DPT+10]=z;	auxBuff[0*DPT+11]=2;
					auxBuff[1*DPT+0]=x;		auxBuff[1*DPT+1]=y;		auxBuff[1*DPT+2]=z+1;	auxBuff[1*DPT+3]=2;
					auxBuff[1*DPT+4]=x;		auxBuff[1*DPT+5]=y+1;	auxBuff[1*DPT+6]=z+1;	auxBuff[1*DPT+7]=2;
					auxBuff[1*DPT+8]=x;		auxBuff[1*DPT+9]=y+1;	auxBuff[1*DPT+10]=z;	auxBuff[1*DPT+11]=2;
					fbuff.put(auxBuff);this.facesNum++;}
					
					if(x+1>=dataset.length||!dataset[x+1][y][z]||!FACE_CULLING){
					auxBuff[0*DPT+0]=x+1;	auxBuff[0*DPT+1]=y;		auxBuff[0*DPT+2]=z;		auxBuff[0*DPT+3]=3;
					auxBuff[0*DPT+4]=x+1;	auxBuff[0*DPT+5]=y+1;	auxBuff[0*DPT+6]=z;		auxBuff[0*DPT+7]=3;
					auxBuff[0*DPT+8]=x+1;	auxBuff[0*DPT+9]=y;		auxBuff[0*DPT+10]=z+1;	auxBuff[0*DPT+11]=3;
					auxBuff[1*DPT+0]=x+1;	auxBuff[1*DPT+1]=y+1;	auxBuff[1*DPT+2]=z;		auxBuff[1*DPT+3]=3;
					auxBuff[1*DPT+4]=x+1;	auxBuff[1*DPT+5]=y+1;	auxBuff[1*DPT+6]=z+1;	auxBuff[1*DPT+7]=3;
					auxBuff[1*DPT+8]=x+1;	auxBuff[1*DPT+9]=y;		auxBuff[1*DPT+10]=z+1;	auxBuff[1*DPT+11]=3;
					fbuff.put(auxBuff);this.facesNum++;}
					
					if(y-1<0||!dataset[x][y-1][z]||!FACE_CULLING){
					auxBuff[0*DPT+0]=x;		auxBuff[0*DPT+1]=y;		auxBuff[0*DPT+2]=z;		auxBuff[0*DPT+3]=4;
					auxBuff[0*DPT+4]=x+1;	auxBuff[0*DPT+5]=y;		auxBuff[0*DPT+6]=z;		auxBuff[0*DPT+7]=4;
					auxBuff[0*DPT+8]=x;		auxBuff[0*DPT+9]=y;		auxBuff[0*DPT+10]=z+1;	auxBuff[0*DPT+11]=4;
					auxBuff[1*DPT+0]=x+1;	auxBuff[1*DPT+1]=y;		auxBuff[1*DPT+2]=z;		auxBuff[1*DPT+3]=4;
					auxBuff[1*DPT+4]=x+1;	auxBuff[1*DPT+5]=y;		auxBuff[1*DPT+6]=z+1;	auxBuff[1*DPT+7]=4;
					auxBuff[1*DPT+8]=x;		auxBuff[1*DPT+9]=y;		auxBuff[1*DPT+10]=z+1;	auxBuff[1*DPT+11]=4;
					fbuff.put(auxBuff);this.facesNum++;}
					
					if(y+1>=dataset.length||!dataset[x][y+1][z]||!FACE_CULLING){
					auxBuff[0*DPT+0]=x;		auxBuff[0*DPT+1]=y+1;	auxBuff[0*DPT+2]=z;		auxBuff[0*DPT+3]=5;
					auxBuff[0*DPT+4]=x;		auxBuff[0*DPT+5]=y+1;	auxBuff[0*DPT+6]=z+1;	auxBuff[0*DPT+7]=5;
					auxBuff[0*DPT+8]=x+1;	auxBuff[0*DPT+9]=y+1;	auxBuff[0*DPT+10]=z;	auxBuff[0*DPT+11]=5;
					auxBuff[1*DPT+0]=x;		auxBuff[1*DPT+1]=y+1;	auxBuff[1*DPT+2]=z+1;	auxBuff[1*DPT+3]=5;
					auxBuff[1*DPT+4]=x+1;	auxBuff[1*DPT+5]=y+1;	auxBuff[1*DPT+6]=z+1;	auxBuff[1*DPT+7]=5;
					auxBuff[1*DPT+8]=x+1;	auxBuff[1*DPT+9]=y+1;	auxBuff[1*DPT+10]=z;	auxBuff[1*DPT+11]=5;
					fbuff.put(auxBuff);this.facesNum++;}
				}
			}
		}
		fbuff.flip();
		return fbuff;
	}
	
	private void saveCurrentCloudToFiles(File path)
	{
	
		/*File f=new File(path,"dump.nc");
		NetcdfFileWriter dataFile=null;
		try {
			dataFile=NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, f.getAbsolutePath());
			Dimension xDim = dataFile.addDimension(null, "x", this.dataset.length);
		    Dimension yDim = dataFile.addDimension(null, "y", this.dataset[0].length);
		    Dimension zDim = dataFile.addDimension(null, "z", this.dataset[0][0].length);
		    // define dimensions
		      List<Dimension> dims = new ArrayList<>();
		      dims.add(xDim);
		      dims.add(yDim);
		      dims.add(zDim);

		      // Define a netCDF variable. The type of the variable in this case
		      // is ncInt (32-bit integer).
		      Variable dataVariable = dataFile.addVariable(null, "data", DataType.DOUBLE, dims);

		      // create the file
		      dataFile.create();

		      // This is the data array we will write. It will just be filled
		      // with a progression of numbers for this example.
		      ArrayDouble.D3 dataOut=new ArrayDouble.D3(xDim.getLength(), yDim.getLength(),zDim.getLength());

		      // Create some pretend data. If this wasn't an example program, we
		      // would have some real data to write, for example, model output.
		      for(int x=0;x<this.dataset.length;x++)
		      {
		    	  for(int y=0;y<this.dataset[0].length;y++)
			      {
		    		  for(int z=0;z<this.dataset[0][0].length;z++)
				      {
		    			  dataOut.set(x,y,z,this.dataset[x][y][z]?1.0:0);
				      }
			      }
		      }

		      // Write the pretend data to the file. Although netCDF supports
		      // reading and writing subsets of data, in this case we write all
		      // the data in one operation.
		      dataFile.write(dataVariable, dataOut);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidRangeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			if(dataFile!=null){
				try {
					dataFile.close();
				} catch (IOException e) {}
			}
		}*/
	}
	
	public FloatBuffer getCurrentBuffer()
	{
		return this.currentBuffer;
	}
	
	public static void main(String[] args) throws LWJGLException, IOException
	{
		String route=null;//"C:/Users/Ivelate/Documents/GLab/TransientVoxelization/paper/results/spadres/gen_ht_dump.dump";/*"C:/Users/Ivelate/Documents/GLab/TransientVoxelization/paper/results/gpures_new/gandalfUpGpuResult.dump";*///"D:/glab/TransientVoxelization/paper/results/bunny/gen_dump.dump";//"result_004.png.dump";//"C:/Users/Ivelate/Documents/GLab/TransientVoxelization/paper/results/gpures/gandalfUpGpuResult.dump";//"res/result_316.png.dump";//"C:/Users/Ivelate/Documents/GLab/TransientVoxelization/MIT_data/fastcamreference/fastcam_lastest/reconstruction/result_020.png.dump";
		/*if(args.length<1) {
			System.err.println("Not enough args");
			System.exit(1);
		}*/
		if(args.length>0)route=args[0];
		else{
			TransientVoxelizationGuiFrame w=new TransientVoxelizationGuiFrame();
			if(w.waitForClose()){
				route=w.getSelectedFile();
			}
			else System.exit(0);
		}
		
		int initialRotation=0;
		for(int i=1;i<args.length;i++)
		{
			switch(args[i])
			{
			case "-inverted":
				initialRotation=2;
				break;
			}
		}
		
		try{
			TransientVoxelization.loadNatives();
		}
		catch(Exception ex){
			if(args.length>0){
				System.err.println("Error loading native libraries. Program will try to keep executing, but something can fail.");
				ex.printStackTrace();
			}
		}
		TransientVisualizerParams params=new TransientVisualizerParams();
		params.route=route;
		params.initialScreenRotationState=initialRotation;
		new TransientVisualizer(params);
		
		System.out.println("Fin!");
	}
}
