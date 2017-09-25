package visualizer.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.SystemColor;

import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import visualizer.gui.TransientVoxelizationGuiFrame.MenuState;

import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.io.File;
import java.awt.event.ActionEvent;
import javax.swing.JTextField;
import javax.swing.ImageIcon;

public class TransientVoxelizationGuiMain extends JPanel {

	/**
	 * Create the panel.
	 */
	public TransientVoxelizationGuiMain(final TransientVoxelizationGuiFrame mainFrame) 
	{
		setSize(500,370);
		setLayout(null);

		JButton btnBack = new JButton("Usage");
		btnBack.setBounds(10, 336, 87, 23);
		btnBack.addActionListener(new ActionListener() {
		       public void actionPerformed(ActionEvent ae){
		    	   mainFrame.setState(MenuState.SHOW_CONTROLS);
		       }
		      });
		add(btnBack);
		
		final JFileChooser dumpChooser = new JFileChooser(new File(System.getProperty("user.dir")));
		dumpChooser.setFileFilter(new DumpFileFilter());
		
		JLabel lblNewLabel = new JLabel("Backprojection Volume Visualizer");
		lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lblNewLabel.setFont(new Font("Tahoma", Font.PLAIN, 24));
		lblNewLabel.setBounds(21, 45, 469, 84);
		add(lblNewLabel);
		
		JButton btnBrowseDumpFile = new JButton("Open volume and visualize");
		btnBrowseDumpFile.setBounds(270, 336, 220, 23);
		btnBrowseDumpFile.addActionListener(new ActionListener() {
		       public void actionPerformed(ActionEvent ae){
		    	   int returnVal=dumpChooser.showOpenDialog(mainFrame);
		    	   if(returnVal==JFileChooser.APPROVE_OPTION) {
		    		   mainFrame.setSelectedFile(dumpChooser.getSelectedFile().getAbsolutePath());
		    		   mainFrame.setState(MenuState.START);
		    	   }
		    	   
		       }
		      });
		add(btnBrowseDumpFile);
		
		JLabel label = new JLabel("");
		label.setIcon(new ImageIcon(TransientVoxelizationGuiMain.class.getResource("/visualizer/gui/Graphics_and_imaging_lab.png")));
		label.setBounds(31, 140, 220, 77);
		add(label);
		
		JLabel label_1 = new JLabel("");
		label_1.setIcon(new ImageIcon(TransientVoxelizationGuiMain.class.getResource("/visualizer/gui/logoUZ.png")));
		label_1.setBounds(270, 140, 220, 84);
		add(label_1);
		
		JLabel lblc = new JLabel("\u00A9 2017 Victor Arellano (ivelate)");
		lblc.setHorizontalAlignment(SwingConstants.CENTER);
		lblc.setFont(new Font("Tahoma", Font.PLAIN, 15));
		lblc.setBounds(10, 266, 480, 23);
		add(lblc);
	}
}
