package org.example;

import io.jbotsim.core.Node;
import io.jbotsim.core.Topology;
import io.jbotsim.ui.JViewer;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        Topology tp = new Topology(800, 600); // taille fenêtre
   Node

        tp.setDefaultNodeModel(Node.class);
        tp.setCommunicationRange(150);

        new JViewer(tp);  // <-- fenêtre graphique

        tp.start();
    }
}