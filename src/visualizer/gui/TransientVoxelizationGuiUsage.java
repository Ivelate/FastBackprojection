package visualizer.gui;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import visualizer.gui.TransientVoxelizationGuiFrame.MenuState;
import javax.swing.SwingConstants;

public class TransientVoxelizationGuiUsage extends JPanel {

	/**
	 * Create the panel.
	 */
	public TransientVoxelizationGuiUsage(final TransientVoxelizationGuiFrame mainFrame) 
	{
		setSize(500,370);
		setLayout(null);

		JButton btnBack = new JButton("Back");
		btnBack.setBounds(10, 336, 87, 23);
		btnBack.addActionListener(new ActionListener() {
		       public void actionPerformed(ActionEvent ae){
		    	   mainFrame.setState(MenuState.MAIN_MENU);
		       }
		      });
		add(btnBack);
		
		
		JLabel lblNewLabel = new JLabel("Backprojection Volume Visualizer");
		lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lblNewLabel.setFont(new Font("Tahoma", Font.PLAIN, 24));
		lblNewLabel.setBounds(21, 45, 469, 84);
		add(lblNewLabel);
		
		JLabel lblNewLabel_1 = new JLabel("M: Increase threshold (+SHIFT/CTRL to fine-tune)");
		lblNewLabel_1.setBounds(21, 197, 261, 14);
		add(lblNewLabel_1);
		
		JLabel lblMapCreationWindow = new JLabel("Controls");
		lblMapCreationWindow.setFont(new Font("Tahoma", Font.PLAIN, 18));
		lblMapCreationWindow.setBounds(21, 140, 224, 31);
		add(lblMapCreationWindow);
		
		JLabel lblSpaceJump = new JLabel("N: Decrease threshold  (+SHIFT/CTRL to fine-tune)");
		lblSpaceJump.setBounds(21, 222, 261, 14);
		add(lblSpaceJump);
		
		JLabel lblShiftToggleFlying = new JLabel("SPACE: Redraw volume");
		lblShiftToggleFlying.setBounds(21, 247, 261, 14);
		add(lblShiftToggleFlying);
		
		JLabel lblMouseWheelUpdown = new JLabel("SHIFT + SPACE: Toggle auto volume redraw");
		lblMouseWheelUpdown.setBounds(21, 272, 308, 14);
		add(lblMouseWheelUpdown);
		
		JLabel lblMouseLeftClick = new JLabel("I: Invert volume");
		lblMouseLeftClick.setBounds(279, 197, 211, 14);
		add(lblMouseLeftClick);
		
		JLabel lblMouseRightClick = new JLabel("SHIFT + I: Rotate volume 90\u00BA");
		lblMouseRightClick.setBounds(279, 222, 211, 14);
		add(lblMouseRightClick);
		
		JLabel lblSelect = new JLabel("Mouse wheel: Zoom in/out");
		lblSelect.setBounds(279, 247, 211, 14);
		add(lblSelect);
		
		JLabel lblCtrl = new JLabel("CTRL + SPACE: Stop/Restart volume rotation");
		lblCtrl.setBounds(21, 297, 345, 14);
		add(lblCtrl);
		
		JLabel label = new JLabel("ESC: Exit");
		label.setBounds(279, 272, 211, 14);
		add(label);
	}

}
