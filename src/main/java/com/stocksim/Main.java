package com.stocksim;
import com.stocksim.ui.MainWindow;import javax.swing.SwingUtilities;
public class Main {public static void main(String[] args){SwingUtilities.invokeLater(()->{MainWindow w=new MainWindow();w.setVisible(true);});}}
