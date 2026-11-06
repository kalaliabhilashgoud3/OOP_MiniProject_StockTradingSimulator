package com.stocksim;

import com.stocksim.ui.MainWindow;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Starts the Swing application on the Event Dispatch Thread. */
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new MainWindow().setVisible(true);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(null,
                        "The simulator could not start. Check the CSV files in the data folder.\n\n" + ex.getMessage(),
                        "Stock Trading Simulator", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
