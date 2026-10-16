package com.gearoptimizer.ui;

import com.gearoptimizer.GearDatabaseManager;
import com.gearoptimizer.InventorySetupsExporter;
import com.gearoptimizer.PersistentBankCache;
import com.gearoptimizer.SetupOptimizer;
import com.gearoptimizer.SetupOptimizer.OptimizationResult;
import com.gearoptimizer.model.ActivityGear;
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
import java.util.Map;
import net.runelite.client.callback.ClientThread;

public class GearOptimizerPanel extends PluginPanel
{
	private final GearDatabaseManager dbManager;
	private final SetupOptimizer optimizer;
	private final InventorySetupsExporter exporter;
	private final PersistentBankCache bankCache;
	private final ItemManager itemManager;
	private final ClientThread clientThread;

	private final JComboBox<ActivityItem> activityCombo = new JComboBox<>();
	private final JLabel bankStatusLabel = new JLabel("Bank: 0 items cached");
	private final JLabel messageLabel = new JLabel("Select an activity above");
	private final JPanel resultsPanel = new JPanel();
	private final JButton copyButton = new JButton("Copy to Inventory Setups");

	private OptimizationResult currentResult;

	private static class ActivityItem
	{
		final String id;
		final String name;

		ActivityItem(String id, String name)
		{
			this.id = id;
			this.name = name;
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

		activityCombo.setAlignmentX(LEFT_ALIGNMENT);
		activityCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JButton optimizeButton = new JButton("Generate Best Setup");
		optimizeButton.setAlignmentX(LEFT_ALIGNMENT);
		optimizeButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		optimizeButton.addActionListener(e -> runOptimization());

		messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		messageLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		messageLabel.setAlignmentX(LEFT_ALIGNMENT);

		panel.add(titleLabel);
		panel.add(Box.createVerticalStrut(4));
		panel.add(bankStatusLabel);
		panel.add(Box.createVerticalStrut(10));
		panel.add(new JLabel("Target Activity / Boss:"));
		panel.add(Box.createVerticalStrut(4));
		panel.add(activityCombo);
		panel.add(Box.createVerticalStrut(8));
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

		copyButton.setEnabled(false);
		copyButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		copyButton.addActionListener(e -> {
			if (currentResult != null)
			{
				boolean ok = exporter.copySetupToClipboard(currentResult);
				if (ok)
				{
					messageLabel.setText("Copied! Paste into Inventory Setups.");
				}
			}
		});

		JButton updateDbButton = new JButton("Check Wiki Database Update");
		updateDbButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		updateDbButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		updateDbButton.addActionListener(e -> {
			dbManager.checkRemoteUpdate();
			refreshActivities();
			messageLabel.setText("Checked database updates (v" + dbManager.getVersion() + ")");
		});

		panel.add(copyButton);
		panel.add(Box.createVerticalStrut(4));
		panel.add(updateDbButton);

		return panel;
	}

	public void refreshActivities()
	{
		DefaultComboBoxModel<ActivityItem> model = new DefaultComboBoxModel<>();
		Map<String, ActivityGear> activities = dbManager.getActivities();

		for (ActivityGear activity : activities.values())
		{
			model.addElement(new ActivityItem(activity.getId(), activity.getName()));
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

			SwingUtilities.invokeLater(() -> {
				currentResult = result;
				displayResults(result, names);
				copyButton.setEnabled(true);
				exporter.copySetupToClipboard(result);
				messageLabel.setText("Auto-copied to clipboard!");
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

