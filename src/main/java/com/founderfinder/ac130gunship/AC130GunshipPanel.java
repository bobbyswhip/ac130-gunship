package com.founderfinder.ac130gunship;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class AC130GunshipPanel extends PluginPanel
{
	private static final Color BG_COLOR = ColorScheme.DARK_GRAY_COLOR;
	private static final Color SECTION_COLOR = new Color(30, 30, 36);
	private static final Color ACCENT = new Color(99, 102, 241);
	private static final Color RED = new Color(239, 68, 68);
	private static final Color GREEN = new Color(34, 197, 94);
	private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 14);
	private static final Font SECTION_FONT = new Font("Arial", Font.BOLD, 11);
	private static final Font LABEL_FONT = new Font("Arial", Font.PLAIN, 11);

	private final AC130GunshipPlugin plugin;
	private final AC130GunshipConfig config;

	private final JLabel ac130Status;
	private final JButton ac130Toggle;

	private Timer refreshTimer;

	public AC130GunshipPanel(AC130GunshipPlugin plugin, AC130GunshipConfig config)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;

		setBackground(BG_COLOR);
		setLayout(new BorderLayout());

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(BG_COLOR);
		content.setBorder(new EmptyBorder(8, 8, 8, 8));

		// Title
		JLabel title = new JLabel("AC-130 Gunship");
		title.setFont(TITLE_FONT);
		title.setForeground(ACCENT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(title);
		content.add(Box.createVerticalStrut(4));

		JLabel subtitle = new JLabel("MW2-style killstreak mode");
		subtitle.setFont(LABEL_FONT);
		subtitle.setForeground(Color.GRAY);
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(subtitle);
		content.add(Box.createVerticalStrut(12));

		// AC-130 Status & Toggle
		JPanel ac130Section = createSection("Status");

		JPanel ac130StatusRow = new JPanel(new BorderLayout(4, 0));
		ac130StatusRow.setOpaque(false);

		ac130Status = new JLabel("Inactive");
		ac130Status.setFont(LABEL_FONT);
		ac130Status.setForeground(Color.GRAY);
		ac130StatusRow.add(ac130Status, BorderLayout.WEST);

		ac130Toggle = new JButton("Activate");
		ac130Toggle.setFont(SECTION_FONT);
		ac130Toggle.setFocusPainted(false);
		ac130Toggle.setBackground(GREEN);
		ac130Toggle.setForeground(Color.WHITE);
		ac130Toggle.setPreferredSize(new Dimension(80, 24));
		ac130Toggle.addActionListener(e -> plugin.toggleAC130());
		ac130StatusRow.add(ac130Toggle, BorderLayout.EAST);

		ac130Section.add(ac130StatusRow);
		ac130Section.add(Box.createVerticalStrut(4));

		JLabel ac130Hint = new JLabel("Alt to toggle | 1/2/3 switch weapons");
		ac130Hint.setFont(new Font("Arial", Font.ITALIC, 10));
		ac130Hint.setForeground(new Color(255, 255, 255, 80));
		ac130Section.add(ac130Hint);

		content.add(wrapSection(ac130Section));

		JScrollPane scrollPane = new JScrollPane(content);
		scrollPane.setBorder(null);
		scrollPane.setBackground(BG_COLOR);
		scrollPane.getViewport().setBackground(BG_COLOR);
		add(scrollPane, BorderLayout.CENTER);

		refreshTimer = new Timer(1000, e -> refreshStatus());
		refreshTimer.start();
	}

	public void refreshStatus()
	{
		SwingUtilities.invokeLater(() ->
		{
			AC130Manager mgr = plugin.getAc130Manager();
			if (mgr == null)
			{
				return;
			}

			boolean active = mgr.isActive();
			if (active)
			{
				ac130Status.setText(mgr.getCurrentWeapon().getDisplayName()
					+ " | Kills: " + mgr.getKills());
				ac130Status.setForeground(RED);
				ac130Toggle.setText("Deactivate");
				ac130Toggle.setBackground(RED);
			}
			else
			{
				ac130Status.setText("Inactive");
				ac130Status.setForeground(Color.GRAY);
				ac130Toggle.setText("Activate");
				ac130Toggle.setBackground(GREEN);
			}
		});
	}

	private JPanel createSection(String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);

		JLabel label = new JLabel(title);
		label.setFont(SECTION_FONT);
		label.setForeground(ACCENT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(label);
		panel.add(Box.createVerticalStrut(4));

		return panel;
	}

	private JPanel wrapSection(JPanel inner)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(SECTION_COLOR);
		wrapper.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(255, 255, 255, 15), 1),
			new EmptyBorder(8, 8, 8, 8)
		));
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height + 200));
		wrapper.add(inner, BorderLayout.CENTER);
		return wrapper;
	}
}
