package visualizer.gui;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.LinkedList;

import javax.swing.JFrame;
import javax.swing.JPanel;


/**
 * This work is licensed under the Creative Commons Attribution 4.0 International License. To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 * 
 * @author Víctor Arellano Vicente (Ivelate)
 * 
 * Menu main class, containing the JFrame and switching between different windows (JPanels) using a state system
 */
public class TransientVoxelizationGuiFrame extends JFrame
{
	public enum MenuState{MAIN_MENU,SHOW_CONTROLS,CLOSED,START}
	
	private final int X_RES=500;
	private final int Y_RES=400;
	
	private static Object lock = new Object(); //Lock
	private MenuState state; //Current menu state
	
	private boolean closed=false;
	private boolean canProcceed=false;
	
	private JPanel currentVisibleWindow=null;
	
	private String selectedFile=null;
	
	public TransientVoxelizationGuiFrame()
	{		
		Dimension size=new Dimension(X_RES,Y_RES);
		Toolkit tk=Toolkit.getDefaultToolkit();
		Dimension screenSize=tk.getScreenSize();
		this.setSize(size);
		setBounds((screenSize.width-this.getWidth())/2,(screenSize.height-this.getHeight())/2,this.getWidth(),this.getHeight());
		
		setTitle("Backprojected volume visualizer");
		setResizable(false);
		
		//Shutdown hook
		//Strong reference to... me
		final TransientVoxelizationGuiFrame me=this;
		addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
            	me.setVisible(false);
                me.setState(MenuState.CLOSED); //If the window closes, sets the window state to CLOSED. It will free the main thread of the menu lock, and it will dispose the menu
                							   //in a controlled way
                e.getWindow().dispose();
            }
        });
		
		setState(MenuState.MAIN_MENU);
		setVisible(true);
	}
	
	public void setSelectedFile(String s)
	{
		this.selectedFile=s;
	}
	public String getSelectedFile()
	{
		return this.selectedFile;
	}
	
	/**
	 * Sets the current menu state to <state>. In function of which state is selected, some actions or others will be performed
	 */
	public void setState(MenuState state)
	{
		this.state=state;
		
		switch(state)
		{
		case MAIN_MENU: 
			setCurrentWindow(new TransientVoxelizationGuiMain(this));
			break;
		case CLOSED: //Closes the screen
			synchronized(lock)
			{
				fullClean();
				lock.notifyAll();
			}
			break;
		case START: //Starts the visualizer
			synchronized(lock)
			{
				fullClean();
				canProcceed=true;
				lock.notifyAll();
			}
			break;
		case SHOW_CONTROLS: //Shows the controls window
			setCurrentWindow(new TransientVoxelizationGuiUsage(this));
			break;
		}
	}

	/**
	 * Sets the current visible window to <window>. The current one will be disposed.
	 */
	private void setCurrentWindow(JPanel window)
	{
		this.setContentPane(window);
		if(this.currentVisibleWindow!=null) this.currentVisibleWindow.invalidate();
		
		this.currentVisibleWindow=window;
		this.currentVisibleWindow.validate();
	}
	
	/**
	 * Disposes the menu
	 */
	public void fullClean()
	{
		this.closed=true;
		this.setVisible(false);
		this.dispose();
	}
	
	/**
	 * Lock method that other classes can use to wait for the menu to close. Returns true if the game can start and false if not.
	 */
	public boolean waitForClose()
	{
		synchronized(lock)
		{
			while(!this.closed)
			{
				try {
					lock.wait();
				} catch (InterruptedException e) {}
			}
		}
		
		return this.canProcceed;
	}
}
