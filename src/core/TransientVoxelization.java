package core;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12.glTexImage3D;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetAttribLocation;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.Util;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.github.ivelate.JavaHDR.HDREncoder;
import com.github.ivelate.JavaHDR.RGBE;

import file.AcceptedFileName;
import file.AcceptedFileName.StreakLaser;
import file.ConfidenceStorage;
import file.HDRDecoder;
import file.LittleEndianDataInputStream;
import file.TransientImage;
import file.TransientInfo;
import geometry.ScreenQuad;
import geometry.Sphere;
import ivengine.IvEngine;
import ivengine.MatrixHelper;
import shader.MaximumPrinterShader;
import shader.VoxelizationShader;

/**
 * Main class. When a TransientVoxelization object is created, it starts automatically performing the transient voxelization based on the provided params
 */
public class TransientVoxelization 
{
	private final static String NATIVE_FOLDER_NAME="TransientVoxelizationLibs/native";
	private final static int CONSERVATIVE_RASTERIZATION_NV=0x9346;
	private static Random rand = new Random();
	
	private final boolean MEMORY_SAVING_MODE; 			//Depending on the number of images to load, loading from disk
														//TWICE but not storing anything in memory can be faster... or at least
														//don't crash the program
	public int SPHERE_MAX_RECURSIONS;
	public float fov;
	
	private final TransientVoxelizationParams params;
	private final int VOXEL_RESOLUTION;
	private final int DELAYED_RENDER_BATCH_SIZE; //Instances to draw at once, 0 to draw all instances in one command. Slower but prevents fatal exceptions on some GPUs
	private float voxelSize;
	
	private VoxelizationShader pnShader;
	private MaximumPrinterShader mpShader;

	private Sphere[] sphereDetailLevels;
	private int[] rejectedEllipsoids; //Just for info purposes
	private ScreenQuad visualizationCanvas;
	
	private ConfidenceStorage confidenceStorage=new ConfidenceStorage(); //Stores the current calculated probability is GPU can't store it all without overflowing
	
	private int voxelStorageTexture;
	private int finalResultTexture;
	private int voxelDepthTexture;
	
	private int finalResultFramebuffer;
	
	private Matrix4f orthoMatrix;

	public float maxIntensity; // Max intensity value of the streak images
	
	public TransientVoxelization(TransientVoxelizationParams params) throws LWJGLException
	{		
		params.validate(); //Corrects or normalizes value ranges of input params if needed
		if(params.AUTO_MANAGE_DISPLAY)  IvEngine.configDisplay(params.VOXEL_RESOLUTION, params.VOXEL_RESOLUTION, "Transient Backprojection", false, false, false);
		this.SPHERE_MAX_RECURSIONS=params.SPHERE_MAX_RECURSIONS;
		this.VOXEL_RESOLUTION=params.VOXEL_RESOLUTION;
		this.DELAYED_RENDER_BATCH_SIZE=params.DELAYED_RENDER_BATCH_SIZE;
		this.fov=params.fov;
		this.MEMORY_SAVING_MODE=params.MEMORY_SAVING_MODE;
		this.orthoMatrix=params.orthoMatrix;
		this.params=params;
		if(!GLContext.getCapabilities().OpenGL44) throw new LWJGLException("OpenGL 4.4 not supported by your GPU");
			
		int[] preprocessingTimes=initResources();
		
		int voxelizationTime=-1;
		boolean errors=false;
		
		try
		{
			voxelizationTime=renderVoxelization(this.orthoMatrix);
				
		} catch (Exception e){
			System.err.println("Error performing GPU backprojection. Full error dump:");
			e.printStackTrace();
			errors=true;
		}
		
		if(params.FORCE_2D_BACKPROJECT||(params.saveImage&&!this.params.backprojectCpu)) this.render2DFiltering(); //Only renders 2D GPU filtering if a 2D image save is requested or if this action is forced by the user

		try
		{
			//Saves all requested files
			File file=params.filename2d;
			File folder=params.saveFolder;
			String extension=(params.DEFAULT_SAVE_AS_HDR?".hdr":".png");
			int cont=0;
			if(file==null)
			{
				do
				{
					String name="result_";
					for(int i=Integer.toString(cont).length();i<3;i++) name=name+"0";
					name=name+cont+extension;
					file=new File(folder,name);
					cont++;
				}while(file.exists());
				if(this.params.saveImage || this.params.save3DDump || this.params.save3DRaw || this.params.save2DRaw) System.out.println("Saving texture to file "+file.getAbsolutePath());
			}
			
			if(this.params.saveImage) {
				if(!this.params.backprojectCpu) this.saveFinalTextureToFile(file); //Saves 2D images generated by the 2D filtering
				else saveFinalTextureToFileCPUApplyDefaultFilters(file,true);
			}
			if(this.params.save3DDump) saveFinalTextureToFileCPUApplyDefaultFilters(params.filename3d==null?new File(file.getName()+".dump"):params.filename3d);
			if(this.params.save3DRaw) saveFinalTextureToFileCPURaw(params.filename3draw==null?new File(file.getName()+"_raw.dump"):params.filename3draw,false);
			if(this.params.save2DRaw) saveFinalTextureToFileCPURaw(params.filename2draw==null?new File(file.getName()+"_raw.png"):params.filename2draw,true);
			
			if(this.params.executionInfoFile!=null) {
				PrintStream iout=new PrintStream(new FileOutputStream(params.executionInfoFile));
				dumpExecutionInfo(iout,params,this.sphereDetailLevels,this.rejectedEllipsoids,voxelizationTime,preprocessingTimes,errors);
				iout.close();
			}
		}
		catch(IOException e){
			System.err.println("Error: Unable to store image in file");
		}
		
		if(params.AUTO_CLEAN) cleanup();
	}
	
	/**
	 * Cleans all resources used by the program (textures, shaders, buffers...). Auto called at the end of the program by default, if AUTO_CLEAN isn't disabled
	 */
	public void cleanup()
	{
		GL11.glDeleteTextures(this.finalResultTexture);
		GL11.glDeleteTextures(this.voxelDepthTexture);
		GL11.glDeleteTextures(this.voxelStorageTexture);
		GL30.glDeleteFramebuffers(this.finalResultFramebuffer);
		for(Sphere s:this.sphereDetailLevels) s.cleanup();
		this.visualizationCanvas.cleanup();
		this.mpShader.fullClean();
		this.pnShader.fullClean();
		
		if(params.AUTO_MANAGE_DISPLAY) Display.destroy();
	}
	
	/**
	 * Renders the backprojection using a orthographic matrix <orthoMatrix>
	 * Returns the time spent rendering, in ms
	 */
	private int renderVoxelization(Matrix4f orthoMatrix)
	{
		GL11.glColorMask(false, false, false, false); //No drawing
		
		long startingTime=System.currentTimeMillis();
		
		pnShader.enable();
		MatrixHelper.uploadMatrix(orthoMatrix, GL20.glGetUniformLocation(pnShader.getID(),"viewProj"));
		GL20.glUniform1i(GL20.glGetUniformLocation(pnShader.getID(), "voxelRes"), VOXEL_RESOLUTION);
		
		int modelMatrixLoc=glGetAttribLocation(pnShader.getID(),"model");
		int intensitiesLoc=glGetAttribLocation(pnShader.getID(),"intensity");
		GL42.glBindImageTexture(0, this.voxelStorageTexture, 0, true, 0, GL15.GL_WRITE_ONLY, GL30.GL_R32UI);
		
		//If overflow protection is enabled, only a safe number of ellipsoids will be rendered before dumping the GPU data into CPU and cleaning the 3D voxel texture
		int maxEllipsoidsUntilDump=params.ALLOW_OVERFLOW_PROTECTION?(int)(((long)(Integer.MAX_VALUE)*2+1)/params.MAX_INTENSITY_MULTIPLIER):Integer.MAX_VALUE;
		int ellipsoidsUntilDump=maxEllipsoidsUntilDump;
		int batchSizeRec=DELAYED_RENDER_BATCH_SIZE;
		for(int d=0;d<this.sphereDetailLevels.length;d++)
		{
			int ellipsoidsToDraw=this.sphereDetailLevels[d].getEllipsoidNumber();
			while(ellipsoidsToDraw>0)
			{
				int drawingAmount=Math.min(ellipsoidsToDraw,ellipsoidsUntilDump);
				ellipsoidsUntilDump-=drawingAmount;
				this.sphereDetailLevels[d].draw(this.pnShader, modelMatrixLoc, intensitiesLoc,batchSizeRec,drawingAmount,this.sphereDetailLevels[d].getEllipsoidNumber()-ellipsoidsToDraw);
				ellipsoidsToDraw-=drawingAmount;
				if(ellipsoidsUntilDump==0){
					System.out.println("Dumping GPU voxel data into CPU");
					this.confidenceStorage.accumulate(get3DTextureData());
					cleanVoxelStorageTexture();
					ellipsoidsUntilDump=maxEllipsoidsUntilDump;
				}
			}
			if(batchSizeRec/2!=0) batchSizeRec=batchSizeRec/2;
		}
		pnShader.disable();
		
		GL11.glFinish();
		if(!this.confidenceStorage.isEmpty()) this.confidenceStorage.accumulate(get3DTextureData()); //Dumps the data from GPU to CPU if needed
		int elapsedTime=(int)(System.currentTimeMillis()-startingTime);
		System.out.println("Fin! Elapsed Time "+elapsedTime+" ms");
		return elapsedTime;
	}
	
	/**
	 * Writes the full 3D backprojected intensity texture into a file, without aplying any filter. Integer format.
	 */
	private void save3DTextureToFile(File file) throws IOException
	{
		//Sometimes this doesn't work tho, too much memory
		IntBuffer buff=BufferUtils.createIntBuffer(VOXEL_RESOLUTION*VOXEL_RESOLUTION*VOXEL_RESOLUTION);
		GL11.glBindTexture(GL_TEXTURE_3D, this.voxelStorageTexture);
		GL11.glGetTexImage(GL_TEXTURE_3D, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, buff);
		
		DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
		out.writeInt(VOXEL_RESOLUTION);
		while(buff.hasRemaining()) out.writeInt(buff.get());
		out.close();
	}
	
	/**
	 * Returns all int values of the backprojected intensity 3D texture
	 */
	private int[] get3DTextureData()
	{
		//Sometimes this doesn't work tho, too much memory
		IntBuffer buff=BufferUtils.createIntBuffer(VOXEL_RESOLUTION*VOXEL_RESOLUTION*VOXEL_RESOLUTION);
		GL11.glBindTexture(GL_TEXTURE_3D, this.voxelStorageTexture);
		GL11.glGetTexImage(GL_TEXTURE_3D, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, buff);
		int[] img3D=new int[buff.limit()];
		buff.get(img3D);
		return img3D;
	}
	
	/**
	 * Wrapper method. Gets the current backprojected intensity 3D data, wether it is on GPU or in CPU. USE ME INSTEAD OF get3DTextureData!!
	 */
	public int[] getCurrent3DData()
	{
		if(this.confidenceStorage.isEmpty()) return get3DTextureData();
		else return this.confidenceStorage.contentsToIntegerList();
	}
	
	/**
	 * Writes the full 3D backprojected intensity texture into a file, aplying a 3D laplacian filter. Float format.
	 * If <image> equals true, saves it in 2D format.
	 */
	private void saveFinalTextureToFileCPUApplyDefaultFilters(File file) throws IOException
	{
		saveFinalTextureToFileCPUApplyDefaultFilters(file,false);
	}
	private void saveFinalTextureToFileCPUApplyDefaultFilters(File file,boolean image) throws IOException
	{
		saveFinalTextureToFileCPUApplyDefaultFilters(file,VOXEL_RESOLUTION,image,params.printGrayscale,getCurrent3DData());
	}

	public float[][][] getFilteredVolumeFromData(int VOXEL_RESOLUTION, int[] data)
	{
		ArrayVoxelContainer container = new ArrayVoxelContainer(VOXEL_RESOLUTION, data);

		float maxIntensityInt = 0;
		float[][][] filtered = new float[VOXEL_RESOLUTION][VOXEL_RESOLUTION][VOXEL_RESOLUTION];
		
		/* LAPLACIAN FILTER */
		for (int z = 1; z < VOXEL_RESOLUTION-1; z++) {
			for (int y = 1; y < VOXEL_RESOLUTION-1; y++) {
				for (int x = 1; x < VOXEL_RESOLUTION-1; x++) {
					float c = container.get(x, y, z);
					float neighbours = 0;
					for (int xf = -1; xf <= 1; xf++) {
						for (int yf = -1; yf <= 1; yf++) {
							for (int zf = -1; zf <= 1; zf++) {
								if (xf!=0 || yf!=0 || zf!=0) {
									neighbours += container.get(x+xf, y+yf, z+zf);
								}
							}
						}
					}
					
					c = 26*c - neighbours;
					
					maxIntensityInt = Math.max(maxIntensityInt, c);
					filtered[x][y][z] = c;
				}
			}
		}
		
		/* Normalize to Final Storage */
		for (int z = 0; z < VOXEL_RESOLUTION; z++) {
			for (int y = 0; y < VOXEL_RESOLUTION; y++) {
				for (int x = 0; x < VOXEL_RESOLUTION; x++) {
					float val = filtered[x][y][z];
					
					if (params.NORMALIZE_TO_UNIT_INTERVAL) {
						val = Math.min(val / maxIntensityInt, 1f);
					} else {
						val = (val/(float)(params.MAX_INTENSITY_MULTIPLIER)) * maxIntensity;
					}
					
					filtered[x][y][z] = val;
				}
			}
		}
		
		return filtered;
	}

	public void saveFinalTextureToFileCPUApplyDefaultFilters(File file, int VOXEL_RESOLUTION, boolean image, boolean grayscale, int[] data) throws IOException
	{
		float[][][] filtered = getFilteredVolumeFromData(VOXEL_RESOLUTION, data);
		
		if (!image) {
			save3DBackprojectionToFile(filtered, file);
		} else {
			save2DBackprojectionToFile(filtered, file, grayscale);
		}
	}
	
	/**
	 * Writes the full 3D backprojected intensity texture into a file, raw format.
	 */
	private void saveFinalTextureToFileCPURaw(File file, boolean image) throws IOException
	{
		ArrayVoxelContainer container=new ArrayVoxelContainer(VOXEL_RESOLUTION,getCurrent3DData());

		float maxIntensityInt = 0;
		float[][][] filtered = new float[VOXEL_RESOLUTION][VOXEL_RESOLUTION][VOXEL_RESOLUTION];
		
		/* COPY RAW DATA */
		for (int z = 0; z < VOXEL_RESOLUTION; z++) {
			for (int y = 0; y < VOXEL_RESOLUTION; y++) {
				for (int x = 0; x < VOXEL_RESOLUTION; x++) {
					float c = container.get(x, y, z);
					
					maxIntensityInt = Math.max(maxIntensityInt, c);
					filtered[x][y][z] = c;
				}
			}
		}
		
		/* Normalize to Final Storage */
		for (int z = 0; z < VOXEL_RESOLUTION; z++) {
			for (int y = 0; y < VOXEL_RESOLUTION; y++) {
				for (int x = 0; x < VOXEL_RESOLUTION; x++) {
					float val = filtered[x][y][z];
					
					if (params.NORMALIZE_TO_UNIT_INTERVAL) {
						val = Math.min(val / maxIntensityInt, 1f);
					} else {
						val = (val/(float)(params.MAX_INTENSITY_MULTIPLIER)) * maxIntensity;
					}
					
					filtered[x][y][z] = val;
				}
			}
		}
		
		if (!image) {
			save3DBackprojectionToFile(filtered, file);
		} else {
			save2DBackprojectionToFile(filtered, file, params.printGrayscale);
		}
	}
	
	/**
	 * Saves a set of data <content> into a file <f>. 
	 * Format intensity (int> data values (float)
	 */
	public static void save3DBackprojectionToFile(float[][][] content,File f) throws IOException
	{
		DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
		
		out.writeInt(-content.length); //Writing length as first data (negative to mark as float file)
		
		for(int x=0;x<content.length;x++)
		{
			for(int y=0;y<content.length;y++)
			{
				for(int z=0;z<content.length;z++)
				{
					out.writeFloat(content[x][y][z]);
				}
			}
		}
		
		out.close();
	}
	
	/**
	 * Saves a set of data <content> into a file <f>. 
	 * Format intensity (int> data values (float)
	 */
	public static void save2DBackprojectionToFile(float[][][] content,File f,boolean grayscale) throws IOException
	{
		//private void saveFinalTextureToFile(long[] img,short[] depths,int res,File file) throws IOException
		long[] img=new long[content.length*content.length];
		short[] depths=new short[content.length*content.length];
		for(int x=0;x<content.length;x++)
		{
			for(int y=0;y<content.length;y++)
			{
				float maxval=0;
				int maxInd=0;
				for(int z=0;z<content.length;z++)
				{
					if(content[x][y][z]>maxval){
						maxval=content[x][y][z];
						maxInd=z;
					}
				}
				img[y*content.length+x]=(long)(maxval * 255);
				depths[y*content.length+x]=(short)maxInd;
			}
		}
		
		saveFinalTextureToFile(img,depths,content.length,f,grayscale);
	}
	
	/**
	 * After performung GPU 2D filtering, gets the resulting 2D image and depth textures and saves them into a file
	 */
	private void saveFinalTextureToFile(File file) throws IOException
	{
		IntBuffer buff=BufferUtils.createIntBuffer(VOXEL_RESOLUTION*VOXEL_RESOLUTION);
		ShortBuffer sbuff=BufferUtils.createShortBuffer(VOXEL_RESOLUTION*VOXEL_RESOLUTION);
		
		GL11.glBindTexture(GL_TEXTURE_2D, this.finalResultTexture);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, buff);
		int[] imgi=new int[buff.limit()];
		buff.get(imgi);
		
		GL11.glBindTexture(GL_TEXTURE_2D, this.voxelDepthTexture);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, sbuff);
		short[] depths=new short[sbuff.limit()];
		sbuff.get(depths);
		
		//In java ints are signed, in GLSL are unsigned... passing all to unsigned longs
		long[] img=new long[imgi.length];
		for(int i=0;i<imgi.length;i++) img[i]=imgi[i] & 0x00000000ffffffffL;//img[i]=Integer.toUnsignedLong(imgi[i]); Not in Java 7 :(

		saveFinalTextureToFile(img, depths, VOXEL_RESOLUTION, file,params.printGrayscale);
	}
	private static void saveFinalTextureToFile(long[] img,short[] depths,int res,File file,boolean grayscale) throws IOException
	{
		String fileLower=file.getName().toLowerCase();
		if(fileLower.endsWith(".hdr")) saveFinalTextureToFileHDR(img,depths,res,file,grayscale);
		else saveFinalTextureToFilePng(img,depths,res,file,grayscale);
	}
	private static void saveFinalTextureToFilePng(long[] img,short[] depths,int res,File file,boolean grayscale) throws IOException
	{
		long maxvalue=0;
		for(int i=0;i<img.length;i++) if(maxvalue<img[i]) maxvalue=img[i];
		System.out.println("Max value= "+maxvalue); //|TODO debug
		BufferedImage off_Image =
				  new BufferedImage(res, res,
				                    BufferedImage.TYPE_INT_RGB);
		BufferedImage depth_Image =
				  new BufferedImage(res, res,
				                    BufferedImage.TYPE_INT_RGB);

		if(maxvalue>0){
			for(int x=0;x<res;x++) for(int y=0;y<res;y++) {

				int c=(int)((img[x*res+y]*255) / maxvalue);
				int d=depths[x*res + y]*255 / res;
				
				off_Image.setRGB(y, x, getRgbFor(c,grayscale));
				depth_Image.setRGB(y, x, c>0?getRgbFor(d,grayscale):0);
			}
		}
	
		ImageIO.write(off_Image, "PNG", file);
		ImageIO.write(depth_Image, "PNG", new File(file.getParentFile(),file.getName().replaceFirst("[.][^.]+$", "")+"_depth.png"));
	}
	private static void saveFinalTextureToFileHDR(long[] img,short[] depths,int res,File file,boolean grayscale) throws IOException
	{
		double maxValue=0;
		for(int i=0;i<img.length;i++) if(maxValue<img[i]) maxValue=img[i];
		
		//Get RGBE from data[] values and parse it into bdata. Only RE components are needed as R=G=B
		byte[] bdata=new byte[img.length*2];
		//Fill bdata
		for(int s=0;s<img.length;s+=res) for(int i=0;i<res;i++) RGBE.float2re(bdata, (float)(img[i+s]/maxValue), i +s*2, res); //Contiguous in the scanline
		
		HDREncoder.writeHDR(bdata,res,res,false,file);
	}
	/**
	 * Returns a RGB value for a intensity value ranging from 0 to 255. If <grayscale> is not enabled, the rgb scale will be the Matlab Jet Scale
	 */
	public static int getRgbFor(int val,boolean grayscale)
	{
		if(grayscale) return val << 16 | val << 8 | val;
		else{
			//JET COLOR SCALE - MATLAB
			float nval=val/255f;
			int[] colors={0x00007F,0x0000FF,0x007FFF,0x00FFFF,0x7FFF7F,0xFFFF00,0xFF7F00,0xFF0000,0x7F0000};
			float interval=1f/(colors.length-1);
			int index=(int)(nval/interval);
			float clerp=(nval%interval)*(colors.length-1);
			return index>=colors.length-1?colors[colors.length-1]:lerp(colors[index],colors[index+1],clerp);
		}
	}
	private static int lerp(int c1,int c2,float amount)
	{
		int b=(c1 & 0xFF); b=(int)(b+((c2 & 0xFF)-b)*amount);
		int g=((c1 >> 8) & 0xFF); g=(int)(g+(((c2 >> 8) & 0xFF)-g)*amount);
		int r=((c1 >> 16) & 0xFF); r=(int)(r+(((c2 >> 16) & 0xFF)-r)*amount);
		return (r<<16)|(g<<8)|b;
	}
	
	/**
	 * Performs 2D GPU filtering from the (already obtained) 3D backprojected intensity texture
	 */
	private void render2DFiltering()
	{
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.finalResultFramebuffer);
		GL11.glColorMask(true, true, true, true); 
		this.mpShader.enable();
		GL20.glUniform1i(GL20.glGetUniformLocation(mpShader.getID(), "voxelRes"), VOXEL_RESOLUTION);
		GL42.glBindImageTexture(0, this.voxelStorageTexture, 0, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
		this.visualizationCanvas.draw(this.mpShader);
		this.mpShader.disable();
	}
	
	/**
	 * Creates a ortho matrix for a given boundaries, performing z-ordering using a lower-to-higher approximation.
	 */
	private Matrix4f createVoxelizationOrthoMatrix(float xi,float yi,float zi,float sx,float sy,float sz)
	{
		Matrix4f toRet=MatrixHelper.createOrthoMatix(sx+xi, xi, sy+yi, yi, zi+sz, zi);
		Matrix4f invz=new Matrix4f();invz.m22=-1; //Fixing shitty non intuitive orientation of Ortho matrixes (Higher to lower)
		Matrix4f.mul(invz, toRet, toRet);

		return toRet;
	}
	
	/**
	 * Inits program resources
	 * @return index 0: streak loading time. index 1: ellipsoid matrix creation time. index 2: ellipsoid insertion time
	 */
	private int[] initResources()
	{
		GL11.glClearColor(0,0,0,0);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable( GL11.GL_BLEND );
		GL11.glEnable(GL_TEXTURE_3D);
		GL11.glEnable(GL_TEXTURE_2D);
		GL11.glViewport(0, 0, VOXEL_RESOLUTION, VOXEL_RESOLUTION);

		float ORTHO_BIG=Math.min(Math.min(params.ORTHO_SIZEX, params.ORTHO_SIZEY),params.ORTHO_SIZEZ); //|TODO in the future multi-size boundaries will be supported, uncomment this when its done
		this.voxelSize=params.voxelSize;
		float ORTHO_OFFSETX=this.params.ORTHO_OFFSETX;
		float ORTHO_OFFSETY=this.params.ORTHO_OFFSETY;
		float ORTHO_OFFSETZ=this.params.ORTHO_OFFSETZ;

		if(this.orthoMatrix==null) {
			this.orthoMatrix=this.createVoxelizationOrthoMatrix(ORTHO_OFFSETX,ORTHO_OFFSETY,ORTHO_OFFSETZ,ORTHO_BIG,ORTHO_BIG,ORTHO_BIG);
			this.voxelSize=ORTHO_BIG/VOXEL_RESOLUTION;
			System.out.println("Voxel size: "+this.voxelSize);
		}

		pnShader=new VoxelizationShader();
		this.mpShader=new MaximumPrinterShader();
		
		this.sphereDetailLevels=new Sphere[SPHERE_MAX_RECURSIONS];
		this.rejectedEllipsoids=new int[SPHERE_MAX_RECURSIONS];
		for(int i=0;i<this.sphereDetailLevels.length;i++) this.sphereDetailLevels[i]=new Sphere(i,params.VERBOSE,params.USE_HALF_ELLIPSOIDS);
		this.visualizationCanvas=new ScreenQuad();
		
		long iloadingtime=System.currentTimeMillis();
		int[] time=new int[3];
		int[] times_2=getEllipsoidMatrixes(this.sphereDetailLevels);
		time[0]=times_2[0]; time[1]=times_2[1];System.out.println(Util.translateGLErrorString(GL11.glGetError()));
		System.out.println("Streak loading complete. Time: "+((float)(System.currentTimeMillis()-iloadingtime)/1000)+"s");
		FloatBuffer fbuff=BufferUtils.createFloatBuffer(16*1024);
		ByteBuffer bbuff=BufferUtils.createByteBuffer(1024);
		
		long t_ellipsoidloading=System.currentTimeMillis();
		for(int i=0;i<this.sphereDetailLevels.length;i++) this.sphereDetailLevels[i].initBuffers(fbuff, bbuff);
		time[2]=(int)(System.currentTimeMillis()-t_ellipsoidloading);
		
		//Create 3D texture to store voxels
		
		this.voxelStorageTexture=glGenTextures();

		glActiveTexture(GL13.GL_TEXTURE0); //Texture 0 is the 3d objective texture 
		glBindTexture(GL_TEXTURE_3D, voxelStorageTexture); 
		glTexParameteri(GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); 
		glTexParameteri(GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		
		//Using the maximum precision offered by the current drivers (GL_R32UI), but a 64bit one would be handy if it existed
		glTexImage3D(GL_TEXTURE_3D,0,GL30.GL_R32UI,VOXEL_RESOLUTION,VOXEL_RESOLUTION,VOXEL_RESOLUTION,0, GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_INT,(IntBuffer)null);
		
		if(params.CLEAR_STORAGE_ON_INIT) cleanVoxelStorageTexture();


		//FINAL FRAMEBUFFER
		this.finalResultFramebuffer=GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, finalResultFramebuffer);
		
		this.finalResultTexture=glGenTextures();
		glActiveTexture(GL13.GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D,this.finalResultTexture);
		glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); 
		glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexImage2D(GL_TEXTURE_2D, 0,GL30.GL_R32UI, VOXEL_RESOLUTION, VOXEL_RESOLUTION, 0,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_INT, (IntBuffer)null);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, finalResultTexture, 0);
		
		this.voxelDepthTexture=glGenTextures();
		glActiveTexture(GL13.GL_TEXTURE2);
		glBindTexture(GL_TEXTURE_2D,this.voxelDepthTexture);
		glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); 
		glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexImage2D(GL_TEXTURE_2D, 0,GL30.GL_R16UI, VOXEL_RESOLUTION, VOXEL_RESOLUTION, 0,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_SHORT, (ShortBuffer)null);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, voxelDepthTexture, 0);
		
		
		IntBuffer drawBuffer=BufferUtils.createIntBuffer(2);
		drawBuffer.put(GL30.GL_COLOR_ATTACHMENT0);
		drawBuffer.put(GL30.GL_COLOR_ATTACHMENT1);
		drawBuffer.flip();
		GL20.glDrawBuffers(drawBuffer);
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		
		System.out.println(Util.translateGLErrorString(GL11.glGetError()));
		
		//trying enabling conservative rasterization via hardware, if supported
		//Would cause false increased intensity measurements on the edges of the triangles, so using it is not advised.
		if(params.ENABLE_HARDWARE_CONSERVATIVE_RASTER)
		{
			GL11.glEnable(CONSERVATIVE_RASTERIZATION_NV);
			if(!GL11.glIsEnabled(CONSERVATIVE_RASTERIZATION_NV)) System.err.println("Warning: Conservative Rasterization not supported by the current hardware");
		}
		
		//Flush possible error 
		GL11.glGetError();	
		
		return time;
	}
	
	/**
	 * Fills the voxel storage texture with 0s
	 */
	private void cleanVoxelStorageTexture()
	{
		//It is initialized to 0, right? Well, maybe not in all GPUs
		//This buffer is used to fill the 3D storage with 0s again
		IntBuffer zeroBuffer=BufferUtils.createIntBuffer(1);
		zeroBuffer.put(0);
		zeroBuffer.flip();
		GL44.glClearTexImage(voxelStorageTexture, 0, GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_INT, zeroBuffer);
	}
	
	/**
	 * Fills each sphere object with the ellispoid matrixes which correspond to its recursion, parsing them from the transient images.
	 * @returns time of streaks loading (in ms) and time of transformation matrix creation (in ms)
	 */
	private int[] getEllipsoidMatrixes(Sphere[] spheres) 
	{
		int[] time=new int[2];
		Vector3f camera=params.camera;
		Vector3f lookTo=params.lookTo;
		Vector3f laserOrigin=params.laserOrigin;
		Vector3f wallNormal=params.wallNormal;
		Vector3f wallDir=params.wallDir;
		float t_delta=params.t_delta;
		float t0=params.t0;
		float streakyratio=params.streakYratio;
		float maximumError=this.voxelSize*params.ERROR_THRESHOLD_WEIGHT; //By default the error will be less than the voxel size, but this can be user-set by a weight
		
		Vector3f[] lasers = params.lasers;

		AcceptedFileName afn=params.acceptedFileName;
		
		float intensityUnit=(float)Math.sqrt(Vector3f.sub(camera, lookTo, null).lengthSquared());
		LinkedList<File> renderFilesList=new LinkedList<File>();
		
		//Get the file names
		if(params.CUSTOM_TRANSIENT_IMAGES==null){
			if(!this.params.inputFolder.isDirectory()) throw new TransientVoxelizationException("The input folder introduced doesn't exist or it's not a valid directory");
			for(File f:this.params.inputFolder.listFiles()){
				if(afn.isAccepted(f)) renderFilesList.add(f);
			}
		}
		File[] files=new File[renderFilesList.size()];
		renderFilesList.toArray(files);
		
		try {
			TransientImage[] imgs=null;
			maxIntensity=0f;
			float[][][] transientStorage=null;
			long ti_streaks=System.currentTimeMillis();
			
			//If using input file transient images, load them
			if(params.CUSTOM_TRANSIENT_IMAGES==null)
			{
				imgs=new TransientImage[files.length];
				if(MEMORY_SAVING_MODE){
					for(int i=0;i<files.length;i++){
						TransientImage img=HDRDecoder.decodeFile(files[i], t_delta,intensityUnit,params.OVERRIDE_TRANSIENT_WALL_POINTS,transientStorage);
						float maxValue=img.getMaxValue();
						if(maxIntensity<maxValue) maxIntensity=maxValue;
						//Heuristic: All transient images are usually of the same size
						if(i==0) transientStorage=new float[img.width][img.height][img.channels];
						//If broken heuristic, disable array reuse
						else if(transientStorage!=null &&(img.width!=transientStorage.length||img.height!=transientStorage[0].length||img.channels!=transientStorage[0][0].length)) transientStorage=null;
					}
				}
				else{
					for(int i=0;i<files.length;i++){
						imgs[i]=TransientVoxelization.initTransientImage(files[i], t_delta,t0,intensityUnit, lasers,fov,streakyratio,camera,lookTo,wallDir,wallNormal,laserOrigin,params.UNWARP_CAMERA,params.UNWARP_LASER,afn,params.OVERRIDE_TRANSIENT_WALL_POINTS);
						if(maxIntensity<imgs[i].getMaxValue()) maxIntensity=imgs[i].getMaxValue();
					}
				}
			}
			//If using custom defined transient images, just find the max intensity over all of them
			else
			{
				imgs=params.CUSTOM_TRANSIENT_IMAGES;
				for(TransientImage img:imgs) if(maxIntensity<img.getMaxValue()) maxIntensity=img.getMaxValue();
			}
			
			//Clamp intensity if needed
			if(params.CLAMP_INTENSITY_GREATER_THAN>0&&maxIntensity>params.CLAMP_INTENSITY_GREATER_THAN) maxIntensity=params.CLAMP_INTENSITY_GREATER_THAN;
			
			time[0]=(int)(System.currentTimeMillis()-ti_streaks);
			
			System.out.println("Max intensity: "+maxIntensity);
			Vector3f aux1=new Vector3f(); Vector3f aux2=new Vector3f(); Vector3f aux3=new Vector3f(); Matrix4f aux4=new Matrix4f(); Vector3f aux5=new Vector3f();//PREALLOCATING
			long ti_ellipsoidcreation=System.currentTimeMillis();
			//Inits the transient images if needed, creates an ellipsoid transformation matrix and intensity for each lit pixel and computes its needed recursion for the provided error threshold
			long allEllipsoids=0;
			TransientInfo inf=null;
			Matrix4f reusableMatrix=null; //if a matrix is discarded, we can reuse it to insert another
			boolean firstInfoPrinted=false;//!params.VERBOSE;
			for(int i=0;i<imgs.length;i++)
			{
				if(imgs[i]==null) {
					long loadI=System.currentTimeMillis();
					imgs[i]=TransientVoxelization.initTransientImage(files[i], t_delta,t0,intensityUnit, lasers,fov,streakyratio,camera,lookTo,wallDir,wallNormal,laserOrigin,params.UNWARP_CAMERA,params.UNWARP_LASER,afn,params.OVERRIDE_TRANSIENT_WALL_POINTS,transientStorage);
					ti_ellipsoidcreation+=System.currentTimeMillis()-loadI; //Correcting time (loading is not taken in account)
				}
				if(params.PRINT_TRANSIENT_IMAGES){
					long printI=System.currentTimeMillis();
					imgs[i].printToFile(new File("StreakImage_"+i+".hdr"),maxIntensity);
					ti_ellipsoidcreation+=System.currentTimeMillis()-printI; //Correcting time (printing is not taken in account)
				}
				int ellipsoidsPerPixel=params.ELLIPSOID_PER_PIXEL_THRESHOLD_WEIGHT<=0?params.ELLIPSOIDS_PER_PIXEL:(int)Math.round(params.ELLIPSOID_PER_PIXEL_THRESHOLD_WEIGHT*imgs[i].getDeltaTime()/this.voxelSize);
				if(ellipsoidsPerPixel<=0) ellipsoidsPerPixel=1;
				if(!firstInfoPrinted){
					System.out.println("Using "+ellipsoidsPerPixel+" ellipsoids per pixel");
					firstInfoPrinted=true;
				}
				for(int w=0;w<imgs[i].width;w++)
				{
					for(int h=0;h<imgs[i].height;h++)
					{
						inf=imgs[i].get(w, h,aux5);
						//We only extend ellipsoid based on the pixel intensity RED value
						if(!isAll(inf.color,0f)&&(params.CLAMP_INTENSITY_GREATER_THAN<0||inf.color[0]<params.CLAMP_INTENSITY_GREATER_THAN))
						{
							for(int e=0;e<ellipsoidsPerPixel;e++){
								float tim=params.STOCHASTIC?(inf.time + rand.nextFloat()*imgs[i].getDeltaTime()):
									(ellipsoidsPerPixel==1?inf.time : inf.time + (((float)e)/ellipsoidsPerPixel)*imgs[i].getDeltaTime());
								MatScale ellipsoid=this.generateMatrixForEllipsoid(tim, imgs[i].getLaser(), inf.point,imgs[i].getWallNormal(),aux1,aux2,aux3,aux4,reusableMatrix);
								if(reusableMatrix!=null) reusableMatrix=null; //it has been reused already
								int recursions=Math.min(SPHERE_MAX_RECURSIONS-1, Sphere.ICOSAHEDRON_RECURSION_ERRORS.length-1);
								for(int r=0;r<Sphere.ICOSAHEDRON_RECURSION_ERRORS.length-1&&r<SPHERE_MAX_RECURSIONS;r++)
								{
									if(ellipsoid.scale*Sphere.ICOSAHEDRON_RECURSION_ERRORS[r]<maximumError){
										recursions=r;break;
									}
								}
								byte intensity=(byte)((inf.color[0]/maxIntensity)*params.MAX_INTENSITY_MULTIPLIER);
								if(intensity==0) {
									rejectedEllipsoids[recursions]++;
									reusableMatrix=ellipsoid.mat;
								}
								else {
									allEllipsoids++;
									this.sphereDetailLevels[recursions].putEllipsoid(ellipsoid.mat, intensity);
								}
							}
						}
					}
				}
				imgs[i]=null; //Memory recycling
			}
			time[1]=(int)(System.currentTimeMillis()-ti_ellipsoidcreation);
			System.out.println("ELLAPSED "+(System.currentTimeMillis()-ti_ellipsoidcreation));
			for(int i=0;i<rejectedEllipsoids.length;i++) if(rejectedEllipsoids[i]>0) System.out.println("REJECTED R"+i+" "+rejectedEllipsoids[i]+" ellipsoids");
			
			long maxSafeEllipsoids=((long)(Integer.MAX_VALUE)*2+1)/params.MAX_INTENSITY_MULTIPLIER;
			//Maximum range till overflow check (never safe enough!)
			if(allEllipsoids>maxSafeEllipsoids){
				System.err.println("WARNING: Safe amount of ellipsoids exceeded for the current maximum intensity ("+params.MAX_INTENSITY_MULTIPLIER+"). "
						+ "There is risk of overflow, so the final image could be not accurate. Consider setting a lower maximum intensity value using the argument -maxIntensity or using less ellipsoids."
						+ "A lower maximum intensity will, however, lower final image quality.");
				System.err.println("Ellipsoid number: "+allEllipsoids);
				System.err.println("Maximum safe range: "+maxSafeEllipsoids);
			}
		
		} catch (IOException e) {
			System.err.println("Error reading file \"img/cube.float\"");
		}
		
		return time;
	}
	
	/**
	 * Parsers a provided lasers file into a Vector3f[] table. The lasers are read in little endian in pure binary format.
	 */
	public static Vector3f[] parsePositionsFile(File lasersFile) 
	{
		try {
			LittleEndianDataInputStream dis=new LittleEndianDataInputStream(new BufferedInputStream(new FileInputStream(lasersFile)));
			LinkedList<Vector3f> lasersList=new LinkedList<Vector3f>();
			while(dis.available()>0){
				lasersList.add(new Vector3f(dis.readFloat(),dis.readFloat(),dis.readFloat()));
			}
			dis.close();
			Vector3f[] toRet=new Vector3f[lasersList.size()];
			return lasersList.toArray(toRet);
		} catch (IOException e) {
			System.err.println("Error reading lasers file, using default lasers instead");
		}
		return null;
	}
	
	/**
	 * Parsers a provided lasers file into a Vector3f[] table. The lasers are read in text format
	 */
	public static Vector3f[] parsePositionsFileText(File lasersFile) 
	{
		try {
			System.out.println(lasersFile.getAbsolutePath());
			Scanner s=new Scanner(lasersFile);
			s.useLocale(new Locale("en", "US")); //Use dots!!
			LinkedList<Vector3f> lasersList=new LinkedList<Vector3f>();
			while(s.hasNextFloat()){
				lasersList.add(new Vector3f(s.nextFloat(),s.nextFloat(),s.nextFloat()));
			}
			s.close();
			Vector3f[] toRet=new Vector3f[lasersList.size()];
			return lasersList.toArray(toRet);
		} catch (IOException e) {
			System.err.println("Error reading lasers file, using default lasers instead");
		}
		return null;
	}
	
	public static TransientImage initTransientImage(File file,float timeScale,float t0,float intensityUnit,Vector3f[] lasers,float fov,float streakyratio,Vector3f cam,Vector3f lookTo,Vector3f wallDir,Vector3f wallNormal,Vector3f laserOrigin,boolean unwarpCamera,boolean unwarpLaser,AcceptedFileName afn,Vector3f[] customWallValues) throws IOException
	{
		return initTransientImage(file, timeScale,t0,intensityUnit,lasers,fov,streakyratio,cam,lookTo,wallDir,wallNormal,laserOrigin,unwarpCamera,unwarpLaser,afn,customWallValues,null);
	}
	public static TransientImage initTransientImage(File file,float timeScale,float t0,float intensityUnit,Vector3f[] lasers,float fov,float streakyratio,Vector3f cam,Vector3f lookTo,Vector3f wallDir,Vector3f wallNormal,Vector3f laserOrigin,boolean unwarpCamera,boolean unwarpLaser,AcceptedFileName afn,Vector3f[] customWallValues,float[][][] data) throws IOException
	{
		TransientImage toRet;
		StreakLaser sl=afn.getStreakAndLaser(file);

		//It doesn't even applies intensity correction over the streak so whatever, disabled |TODO hi :D
		toRet = data==null?HDRDecoder.decodeFile(file,timeScale,intensityUnit,customWallValues/*,sl.streak*/):HDRDecoder.decodeFile(file,timeScale,intensityUnit,customWallValues,/*sl.streak,*/data);

		toRet.setParamsForCamera(cam,lookTo,wallDir, wallNormal,fov,lasers[sl.laser],sl.streak,streakyratio,t0,unwarpCamera);
		float laserDist=(float)(Math.sqrt((Vector3f.sub(toRet.getLaser(), laserOrigin, null)).lengthSquared()));
		toRet.setLaserHitTime(unwarpLaser?0:laserDist);
		return toRet;
	}
	
	/**
	 * Util method passing through
	 */
	private static final boolean isAll(float[] t,float v)
	{
		for(int i=0;i<t.length;i++)
		{
			if(t[i]!=v)return false;
		}
		return true;
	}
	
	/**
	 * Generates a model matrix for the spheroid space wave generated by a light pulse inciding in P_w=<wallPos>
	 * from P_l=<laserPos> in time <time>
	 */
	private MatScale generateMatrixForEllipsoid(float time,Vector3f laserPos,Vector3f wallPos,Vector3f wallNormal)
	{
		return this.generateMatrixForEllipsoid(time, laserPos, wallPos, wallNormal, new Vector3f(), new Vector3f(), new Vector3f(),new Matrix4f(),null);
	}
	private MatScale generateMatrixForEllipsoid(float time,Vector3f laserPos,Vector3f wallPos,Vector3f wallNormal,Vector3f aux1,Vector3f aux2,Vector3f aux3,Matrix4f aux4,Matrix4f reusableMatrix)
	{
		if(reusableMatrix==null) reusableMatrix=new Matrix4f();
		
		Vector3f translation=aux1; translation.set((laserPos.x+wallPos.x)/2,(laserPos.y+wallPos.y)/2,(laserPos.z+wallPos.z)/2);
		Vector3f vLaserWall=Vector3f.sub(wallPos, laserPos, aux2);
		double focalDist=vLaserWall.length();
		float scaleyz=(float)Math.sqrt((time*time -focalDist*focalDist)/4);
		Vector3f scale=new Vector3f(
				time/2,
				scaleyz,
				scaleyz);

		//vLaserWall is now normalized
		vLaserWall.normalise();
		Matrix4f baseChange;
		if(params.USE_HALF_ELLIPSOIDS) baseChange=MatrixHelper.createBaseChangeMatrixToGlobal(vLaserWall,Vector3f.cross(vLaserWall, wallNormal, aux3),wallNormal,reusableMatrix);
		else
		{
			//Only the x axis is important, as the ellipsoid is extended fully. We auto-find the remaining axes
			Vector3f v=MatrixHelper.yAxis;
			if(Math.abs(vLaserWall.y)>0.99) v=MatrixHelper.xAxis;
			
			Vector3f w=Vector3f.cross(vLaserWall, v, null);	w.normalise();
			baseChange=MatrixHelper.createBaseChangeMatrixToGlobal(vLaserWall,Vector3f.cross(vLaserWall, w, aux3),w,reusableMatrix);
		}
		
		//reusing baseChange memory
		aux4.setIdentity();
		Matrix4f.mul(aux4.translate(translation), baseChange, baseChange);
		baseChange.scale(scale);

		return new MatScale(baseChange,scale.x);
	}
	
	private class MatScale
	{
		public final Matrix4f mat;public final float scale;
		public MatScale(Matrix4f mat,float scale)
		{
			this.mat=mat;this.scale=scale;
		}
	}
	
	
	
	//****************************************************** MAIN *************************************************************//
	public static void main(String[] args)
	{
		TransientVoxelizationParams params=new TransientVoxelizationParams();
		if(!TransientVoxelization.parseArgsIntoParams(params, args)) return; //Parses args into params. If some arg request exit, the program is soft closed
		
		//Load natives
		try{
			
			try{
				TransientVoxelization.loadNatives();
			}
			catch(Exception ex){
				System.err.println("Error loading native libraries. Program will try to keep executing, but something can fail.");
				ex.printStackTrace();
			}
			IvEngine.configDisplay(params.VOXEL_RESOLUTION, params.VOXEL_RESOLUTION, "Transient Backprojection", false, false, false);

			new TransientVoxelization(params);
		}
		catch(Exception e){
			System.out.println("shit");
			e.printStackTrace(System.out);
		}
		finally{
			Display.destroy();
		}
	}
	
	/**
	 * Parses all input <args> and stores it into our handy class <params>. If false is returned, a soft program exit is requested
	 */
	public static boolean parseArgsIntoParams(TransientVoxelizationParams params,String[] args) 
	{
		return parseArgsIntoParams(params,args,false);
	}
	public static boolean parseArgsIntoParams(TransientVoxelizationParams params,String[] args,boolean incompleteParams) //Incomplete params is set to true if this params are not the only ones which are going to be set. It overrides
																														 //standard behaviours such as forcing one image to be printed forcefully and so on
	{
		for(int i=0;i<args.length;i++)
		{
			String expr=args[i];
			try{
				switch(expr)
				{
				case "-i": case "-info": case "-?": case "/?": case "/info": case "/usage": case "-usage":
					printProgramInfo(System.out);
					return false; //Soft ends the program after printing
				case "-inputFolder":
					params.inputFolder=new File(args[++i]);
					break;
				case "-fov":
					params.fov=(float)Math.toRadians(Float.parseFloat(args[++i]));
					break;
				case "-fovrad":
					params.fov=Float.parseFloat(args[++i]);
					break;
				case "-voxelRes":
					params.VOXEL_RESOLUTION=Integer.parseInt(args[++i]);
					break;
				case "-errorThreshold":
					params.ERROR_THRESHOLD_WEIGHT=Float.parseFloat(args[++i]);
					break;
				case "-verbose":
					params.VERBOSE=true;
					break;
				case "-renderBatchSize":
					params.DELAYED_RENDER_BATCH_SIZE=Integer.parseInt(args[++i]);
					break;
				case "-sphereMaxRec":
					params.SPHERE_MAX_RECURSIONS=Integer.parseInt(args[++i]);
					break;
				case "-stochastic":
					params.STOCHASTIC=true;
					break;
				case "-memSave":
					params.MEMORY_SAVING_MODE=true;
					break;
				case "-ortho":
					params.ORTHO_OFFSETX=Float.parseFloat(args[++i]);
					params.ORTHO_OFFSETY=Float.parseFloat(args[++i]);
					params.ORTHO_OFFSETZ=Float.parseFloat(args[++i]);
					float size=Float.parseFloat(args[++i]);
					params.ORTHO_SIZEX=size;
					params.ORTHO_SIZEY=size;
					params.ORTHO_SIZEZ=size;
					break;
				case "-streakYratio":
					params.streakYratio=Float.parseFloat(args[++i]);
					break;
				case "-cam":
					params.camera=new Vector3f(
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i])
							);
					break;
				case "-lookTo":
					params.lookTo=new Vector3f(
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i])
							);
					break;
				case "-laserOrigin":
					params.laserOrigin=new Vector3f(
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i])
							);
					break;
				case "-t_delta":
					params.t_delta=Float.parseFloat(args[++i]);
					break;
				case "-ellipsoidsPerPixel":
					params.ELLIPSOIDS_PER_PIXEL=Integer.parseInt(args[++i]);
					params.ELLIPSOID_PER_PIXEL_THRESHOLD_WEIGHT=-1; //Overriden
					break;
				case "-ellipsoidsPerPixelThreshold":
					params.ELLIPSOID_PER_PIXEL_THRESHOLD_WEIGHT=Float.parseFloat(args[++i]);
					break;
				case "-t0":
					params.t0=Float.parseFloat(args[++i]);
					break;
				case "-lasers":
					LinkedList<Vector3f> lasers=new LinkedList<Vector3f>();
					while(!args[i+1].startsWith("-"))
					{
						lasers.add(new Vector3f(
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i])
							));
					}
					Vector3f[] lasersArray=new Vector3f[lasers.size()];
					params.lasers=lasers.toArray(lasersArray);
					break;
				case "-lasersFile":
					params.lasersFile=new File(args[++i]);
					params.readLasersAsText=false;
					break;
				case "-lasersFileText":
					params.lasersFile=new File(args[++i]);
					params.readLasersAsText=true;
					break;
				case "-wallFile":
					params.wallFile=new File(args[++i]);
					params.readWallAsText=false;
					break;
				case "-wallFileText":
					params.wallFile=new File(args[++i]);
					params.readWallAsText=true;
					break;
				case "-saveFolder":
					params.saveFolder=new File(args[++i]);
					break;
				case "-save2D":
					params.saveImage=true;
					break;
				case "-grayscale":
					params.printGrayscale=true; //Prints the images in grayscale
					break;
				case "-save2Dcpu":
					params.saveImage=true;
					params.backprojectCpu=true;
					break;
				case "-save3D":
					params.save3DDump=true;
					break;
				case "-save3Draw":
					params.save3DRaw=true;
					break;
				case "-save2Draw":
					params.save2DRaw=true;
					break;
				case "-filename":
				case "-filename2d":
					params.saveImage=true;
					params.filename2d=new File(args[++i]);
					break;
				case "-filename2dRaw":
					params.save2DRaw=true;
					params.filename2draw=new File(args[++i]);
					break;
				case "-filename3d":
					params.save3DDump=true;
					params.filename3d=new File(args[++i]);
					break;
				case "-filename3dRaw":
					params.save3DRaw=true;
					params.filename3draw=new File(args[++i]);
					break;
				case "-wallNormal":
					params.wallNormal=new Vector3f(
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i])
							).normalise(null); //Ensure normalized
					break;
				case "-wallDir":
					params.wallDir=new Vector3f(
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i]),
							Float.parseFloat(args[++i])
							).normalise(null); //Ensure normalized
					break;
				case "-maxIntensity":
					params.MAX_INTENSITY_MULTIPLIER=Integer.parseInt(args[++i]);
					break;
				case "-infoFile":
					params.executionInfoFile=new File(args[++i]);
					break;
				case "-extendFullEllipsoids":
					params.USE_HALF_ELLIPSOIDS=false;
					break;
				case "-customTransientWallPoints":
					//If any of the transient images loaded have a incorrect width for this, runtimeException
					List<Vector3f> customPoints=new ArrayList<Vector3f>();
					try
					{
						while(i+1<args.length){
							Float.parseFloat(args[i+1]);
							customPoints.add(new Vector3f(
								Float.parseFloat(args[++i]),
								Float.parseFloat(args[++i]),
								Float.parseFloat(args[++i])
								));
						}
					}
					catch(Exception e){} //Ended (exception on parseFloat)
					
					Vector3f[] arr=new Vector3f[customPoints.size()];
					params.OVERRIDE_TRANSIENT_WALL_POINTS=customPoints.toArray(arr);
					break;
				case "-clampIntensityGreaterThan":
					params.CLAMP_INTENSITY_GREATER_THAN=Float.parseFloat(args[++i]);
					break;
				case "-printTransientImages":
					params.PRINT_TRANSIENT_IMAGES=true;
					break;
				//DEBUG, it will be selected automatically
				case "-loadFromFloat":
					params.acceptedFileName=new AcceptedFileName(2,2,".float");
					break;
				case "-unwarpLaser":
					params.UNWARP_LASER=true;
					break;
				case "-unwarpCamera":
					params.UNWARP_CAMERA=true;
					break;
				case "-saveDumpsUnnormalized":
					params.NORMALIZE_TO_UNIT_INTERVAL=false;
					break;
				case "-disableOverflowProtection":
					params.ALLOW_OVERFLOW_PROTECTION=false;
					break;
				default:
					System.err.println("Unknown arg "+expr);
				}
			}
			catch(Exception e){
				System.err.println("Error parsing arg "+expr);
			}
		}
		
		if(!incompleteParams)
		{
			if(!params.save3DDump&&!params.saveImage&&!params.save3DRaw&&!params.save2DRaw){
				System.out.println("No output save especified. Saving image by default");
				params.saveImage=true;
			}
		}
		
		//Parsing laser file if its provided
		if(params.lasersFile!=null)
		{
			boolean error=false;
			if(params.lasersFile.exists()) {
				Vector3f[] ret=params.readLasersAsText?parsePositionsFileText(params.lasersFile):parsePositionsFile(params.lasersFile);
				System.out.println("LASERS!! "+ret.length);
				if(ret!=null) params.lasers=ret;
				else error=true;
			}
			else error=true;
			if(error) System.err.println("Error parsing lasers file, using default lasers instead");
		}
		if(params.wallFile!=null)
		{
			boolean error=false;
			if(params.wallFile.exists()) {
				Vector3f[] ret=params.readWallAsText?parsePositionsFileText(params.wallFile):parsePositionsFile(params.wallFile);
				System.out.println("Wall points: " + ret.length);
				if(ret!=null) params.OVERRIDE_TRANSIENT_WALL_POINTS=ret;
				else error=true;
			}
			else error=true;
			if(error) System.err.println("Error parsing wall file, using default wall instead");
		}
		
		return true;
	}
	
	/**
	 * Prints program usage info
	 */
	private static void printProgramInfo(PrintStream out)
	{
		try {
			System.out.println(TransientVoxelization.class.getResource("README.txt"));
			Scanner s=new Scanner(TransientVoxelization.class.getResource("/README.txt").openStream());
			while(s.hasNextLine()) out.println(s.nextLine());
			s.close();
		} catch (Exception e) {
			System.err.println("Error reading usage file");
		}
	}
	
	/**
	 * Prints some program execution data into a stream <out>
	 */
	private static void dumpExecutionInfo(PrintStream out,TransientVoxelizationParams params,Sphere[] ellipsoids,int[] rejectedEllipsoids,int time,int[] preprocessingTimes,boolean error)
	{
		if(error)
		{
			out.println("ERRORS DETECTED IN THE EXECUTION");
			out.println();
		}
		try{
			out.println(GL11.glGetString(GL11.GL_VENDOR)+" "+GL11.glGetString(GL11.GL_RENDERER));
			out.println();
		}
		catch(Exception e){}
		out.println("Voxel dimensions  x: "+params.VOXEL_RESOLUTION);
		out.println("                  y: "+params.VOXEL_RESOLUTION);
		out.println("                  z: "+params.VOXEL_RESOLUTION);
		out.println("Memory saving mode: "+params.MEMORY_SAVING_MODE);
		out.println("Max ellipsoid recursions: "+params.SPHERE_MAX_RECURSIONS);
		out.println("Error threshold weight: "+params.ERROR_THRESHOLD_WEIGHT);
		if(params.CLAMP_INTENSITY_GREATER_THAN>0) out.println("Clamping intensity greater than: "+params.CLAMP_INTENSITY_GREATER_THAN);
		out.println();
		boolean first=true;
		out.print("RENDERED ");
		long totalTris=0;
		for(int i=0;i<ellipsoids.length;i++){
			if(ellipsoids[i].getEllipsoidNumber()>0)
			{
				if(!first) out.print("         ");
				first=false;
				long recTris=(long)(ellipsoids[i].getTrianglesPerSphere())*ellipsoids[i].getEllipsoidNumber();
				totalTris+=recTris;
				out.println("rec "+i+": "+ellipsoids[i].getEllipsoidNumber()+" , polygons: "+recTris);
			}
		}
		out.println("Total polygons: "+totalTris);
		first=true;
		out.println();
		out.print("REJECTED ");
		for(int i=0;i<ellipsoids.length;i++){
			if(rejectedEllipsoids[i]>0)
			{
				if(!first) out.print("         ");
				first=false;
				out.println("rec "+i+": "+rejectedEllipsoids[i]);
			}
		}
		out.println();
		out.println("Preprocessing time - Streak image loading: "+preprocessingTimes[0]+"ms");
		out.println("                     Ellipsoid creation: "+preprocessingTimes[1]+"ms"+(params.MEMORY_SAVING_MODE?" (MEMSAVE - Streaks loaded again, time ignored)":"")); //streaks are preprocessed again if memsave is active
		out.println("                     Ellipsoid data GPU dump: "+preprocessingTimes[2]+"ms");
		out.println();
		out.println("Rendering time: "+time+"ms");
		out.println();
		out.println("Total time: "+(time+preprocessingTimes[0]+preprocessingTimes[1]+preprocessingTimes[2])+"ms");
	}
	
	/**
	 * Load native libraries needed for LWJGL to work
	 * @throws UnsupportedOperatingSystemException 
	 */
	public static final void loadNatives() throws IOException, UnsupportedOperatingSystemException
	{
		System.out.println("NATIVE LOAD");
		//Fetch OS
		OsCheck.OperatingSystem os= OsCheck.getOperatingSystemType();
		System.out.println("GETTING "+os.getAsStringName()+" NATIVE LIBS");
		
		File nativesFolder=null;
		try
		{
			File jarFile=new File(TransientVoxelization.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			nativesFolder=new File(jarFile.getParentFile().getPath(),NATIVE_FOLDER_NAME);
		}
		catch(SecurityException | URISyntaxException ex){
			System.out.println("Problem loading natives from path relative to .jar. Loading them from absolute path (execution path)");
			nativesFolder=new File(NATIVE_FOLDER_NAME);
		}
		//Add the OS route
		nativesFolder=new File(nativesFolder,os.getAsStringName());
		
		if(!nativesFolder.exists()||!nativesFolder.isDirectory()||!nativesFolder.canRead()) throw new IOException("Can't read native libraries folder");
		System.setProperty("org.lwjgl.librarypath", nativesFolder.getAbsolutePath());
	}
}
