package com.gearoptimizer.ui;

import com.gearoptimizer.GearDatabaseManager;
import com.gearoptimizer.InventorySetupsExporter;
import com.gearoptimizer.PersistentBankCache;
import com.gearoptimizer.SetupOptimizer;
import com.gearoptimizer.SetupOptimizer.OptimizationResult;
import com.gearoptimizer.model.ActivityGear;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GearOptimizerPanel extends PluginPanel
{
	private final GearDatabaseManager dbManager;
	private final SetupOptimizer optimizer;
	private final InventorySetupsExporter exporter;
	private final PersistentBankCache bankCache;
	private final ItemManager itemManager;
	private final ClientThread clientThread;

	private final JComboBox<String> styleCombo = new JComboBox<>(new String[]{"All Styles", "Melee", "Ranged", "Magic"});
	private final JComboBox<ActivityItem> activityCombo = new JComboBox<>();
	private final JLabel bankStatusLabel = new JLabel("Bank: 0 items cached");
	private final JLabel messageLabel = new JLabel("Select an activity above");
	private final JPanel resultsPanel = new JPanel();
	private final JButton directButton = new JButton("Direct Add to Inventory Setups");
	private final JButton copyButton = new JButton("Copy Setup to Clipboard");

	private OptimizationResult currentResult;

	private static class ActivityItem
	{
		final String id;
		final String name;
		final String style;

		ActivityItem(String id, String name, String style)
		{
			this.id = id;
			this.name = name;
			this.style = style;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public GearOptimizerPanel(
		GearDatabaseManager dbManager,
		SetupOptimizer optimizer,
		InventorySetupsExporter exporter,
		PersistentBankCache bankCache,
		ItemManager itemManager,
		ClientThread clientThread)
	{
		super(false);
		this.dbManager = dbManager;
		this.optimizer = optimizer;
		this.exporter = exporter;
		this.bankCache = bankCache;
		this.itemManager = itemManager;
		this.clientThread = clientThread;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		add(buildTopPanel(), BorderLayout.NORTH);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scrollPane = new JScrollPane(resultsPanel);
		scrollPane.setBorder(null);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scrollPane, BorderLayout.CENTER);

		add(buildBottomPanel(), BorderLayout.SOUTH);

		styleCombo.addActionListener(e -> refreshActivities());

		refreshActivities();
		updateBankStatus();
	}

	private JPanel buildTopPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel titleLabel = new JLabel("Gear Optimizer");
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setAlignmentX(LEFT_ALIGNMENT);

		bankStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
		bankStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		bankStatusLabel.setAlignmentX(LEFT_ALIGNMENT);

		styleCombo.setAlignmentX(LEFT_ALIGNMENT);
		styleCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		activityCombo.setAlignmentX(LEFT_ALIGNMENT);
		activityCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JButton optimizeButton = new JButton("Generate Best Setup");
		optimizeButton.setAlignmentX(LEFT_ALIGNMENT);
		optimizeButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		optimizeButton.setBackground(new Color(40, 110, 60));
		optimizeButton.setForeground(Color.WHITE);
		optimizeButton.addActionListener(e -> runOptimization());

		messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		messageLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		messageLabel.setAlignmentX(LEFT_ALIGNMENT);

		panel.add(titleLabel);
		panel.add(Box.createVerticalStrut(4));
		panel.add(bankStatusLabel);
		panel.add(Box.createVerticalStrut(10));

		JLabel styleLabel = new JLabel("Combat Style Filter:");
		styleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		styleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		panel.add(styleLabel);
		panel.add(Box.createVerticalStrut(3));
		panel.add(styleCombo);
		panel.add(Box.createVerticalStrut(8));

		JLabel activityLabel = new JLabel("Target Activity / Boss:");
		activityLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		activityLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		panel.add(activityLabel);
		panel.add(Box.createVerticalStrut(3));
		panel.add(activityCombo);
		panel.add(Box.createVerticalStrut(10));
		panel.add(optimizeButton);
		panel.add(Box.createVerticalStrut(8));
		panel.add(messageLabel);
		panel.add(Box.createVerticalStrut(10));

		return panel;
	}

	private JPanel buildBottomPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(8, 0, 0, 0));

		directButton.setEnabled(false);
		directButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		directButton.setBackground(new Color(30, 80, 130));
		directButton.setForeground(Color.WHITE);
		directButton.addActionListener(e -> {
			if (currentResult != null)
			{
				boolean ok = exporter.directImportToInventorySetups(currentResult);
				if (ok)
				{
					messageLabel.setText("Added directly to Inventory Setups!");
				}
				else
				{
					messageLabel.setText("Saved to config & copied to clipboard.");
				}
			}
		});

		copyButton.setEnabled(false);
		copyButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		copyButton.addActionListener(e -> {
			if (currentResult != null)
			{
				boolean ok = exporter.copySetupToClipboard(currentResult);
				if (ok)
				{
					messageLabel.setText("Copied setup (JSON) to clipboard!");
				}
			}
		});

		JButton updateDbButton = new JButton("Check Wiki Database Update");
		updateDbButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		updateDbButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		updateDbButton.addActionListener(e -> {
			dbManager.checkRemoteUpdate();
			refreshActivities();
			messageLabel.setText("Checked database updates (v" + dbManager.getVersion() + ")");
		});

		panel.add(directButton);
		panel.add(Box.createVerticalStrut(4));
		panel.add(copyButton);
		panel.add(Box.createVerticalStrut(4));
		panel.add(updateDbButton);

		return panel;
	}

	public void refreshActivities()
	{
		String style = (String) styleCombo.getSelectedItem();
		DefaultComboBoxModel<ActivityItem> model = new DefaultComboBoxModel<>();
		List<ActivityGear> activities = dbManager.getActivitiesByStyle(style);

		for (ActivityGear activity : activities)
		{
			model.addElement(new ActivityItem(activity.getId(), activity.getName(), activity.getStyle()));
		}

		activityCombo.setModel(model);
	}

	public void updateBankStatus()
	{
		int count = bankCache.getItemCount();
		if (count == 0)
		{
			bankStatusLabel.setText("Bank: Empty (Open bank booth to cache)");
			bankStatusLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		}
		else
		{
			bankStatusLabel.setText("Bank: " + count + " items cached");
			bankStatusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
	}

	private void runOptimization()
	{
		ActivityItem selected = (ActivityItem) activityCombo.getSelectedItem();
		if (selected == null)
		{
			return;
		}

		ActivityGear activity = dbManager.getActivity(selected.id);
		if (activity == null)
		{
			return;
		}

		messageLabel.setText("Optimizing gear...");

		clientThread.invokeLater(() -> {
			OptimizationResult result = optimizer.optimize(activity);

			// Pre-resolve item names on the ClientThread
			Map<Integer, String> names = new HashMap<>();
			for (int id : result.getEquippedItems().values())
			{
				try
				{
					names.put(id, itemManager.getItemComposition(id).getName());
				}
				catch (Exception ignored) {}
			}
			for (int id : result.getMissingBisItems().values())
			{
				try
				{
					names.put(id, itemManager.getItemComposition(id).getName());
				}
				catch (Exception ignored) {}
			}

			// Perform direct import / export
			boolean directlyImported = exporter.directImportToInventorySetups(result);

			SwingUtilities.invokeLater(() -> {
				currentResult = result;
				displayResults(result, names);
				directButton.setEnabled(true);
				copyButton.setEnabled(true);

				if (directlyImported && exporter.isInventorySetupsInstalled())
				{
					messageLabel.setText("Optimized & added to Inventory Setups!");
				}
				else
				{
					messageLabel.setText("Optimized! Auto-copied to clipboard.");
				}
			});
		});
	}

	private void displayResults(OptimizationResult result, Map<Integer, String> itemNames)
	{
		resultsPanel.removeAll();

		if (result.getEquippedItems().isEmpty())
		{
			JLabel emptyLabel = new JLabel("No recommended items found in bank.");
			emptyLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			resultsPanel.add(emptyLabel);
		}
		else
		{
			// Combat Style Badge Header
			String style = result.getActivity().getStyle();
			Color styleColor = ColorScheme.LIGHT_GRAY_COLOR;
			if ("Melee".equalsIgnoreCase(style))
			{
				styleColor = new Color(240, 110, 70); // Warm Flame Orange/Red
			}
			else if ("Ranged".equalsIgnoreCase(style))
			{
				styleColor = new Color(70, 210, 100); // Ranger Green
			}
			else if ("Magic".equalsIgnoreCase(style))
			{
				styleColor = new Color(90, 180, 255); // Mystic Cyan/Blue
			}

			JPanel headerPanel = new JPanel(new BorderLayout());
			headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
			headerPanel.setBorder(new EmptyBorder(2, 4, 6, 4));

			JLabel styleHeader = new JLabel(result.getActivity().getName());
			styleHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
			styleHeader.setForeground(styleColor);
			headerPanel.add(styleHeader, BorderLayout.WEST);

			int equippedCount = result.getEquippedItems().size();
			int totalSlots = result.getActivity().getSlots().size();
			JLabel matchLabel = new JLabel(equippedCount + "/" + totalSlots + " Slots", SwingConstants.RIGHT);
			matchLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
			matchLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			headerPanel.add(matchLabel, BorderLayout.EAST);

			resultsPanel.add(headerPanel);
			resultsPanel.add(Box.createVerticalStrut(4));

			for (Map.Entry<String, Integer> entry : result.getEquippedItems().entrySet())
			{
				String slot = entry.getKey();
				int itemId = entry.getValue();
				String itemName = itemNames.getOrDefault(itemId, "Item " + itemId);

				JPanel slotRow = new JPanel(new BorderLayout());
				slotRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				slotRow.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
					new EmptyBorder(4, 6, 4, 6)
				));

				JLabel slotLabel = new JLabel(capitalize(slot) + ":");
				slotLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				slotLabel.setPreferredSize(new Dimension(65, 18));

				JLabel itemLabel = new JLabel(itemName);
				itemLabel.setForeground(Color.WHITE);

				slotRow.add(slotLabel, BorderLayout.WEST);
				slotRow.add(itemLabel, BorderLayout.CENTER);

				// Show Best-in-Slot upgrade notice if player doesn't have the #1 item
				Integer bisId = result.getMissingBisItems().get(slot);
				if (bisId != null && bisId != itemId)
				{
					String bisName = itemNames.getOrDefault(bisId, "Item " + bisId);
					JLabel upgradeLabel = new JLabel("BIS: " + bisName, SwingConstants.RIGHT);
					upgradeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
					upgradeLabel.setForeground(new Color(255, 175, 75));
					slotRow.add(upgradeLabel, BorderLayout.SOUTH);
				}

				resultsPanel.add(slotRow);
				resultsPanel.add(Box.createVerticalStrut(2));
			}
		}

		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private String capitalize(String str)
	{
		if (str == null || str.isEmpty()) return str;
		return Character.toUpperCase(str.charAt(0)) + str.substring(1);
	}
}
