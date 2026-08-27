package common;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLReader {
    // DocumentBuilderFactory factory;
    Document doc; 
    XPath xPath;

    final String path;
    
    public XMLReader(String path) {
        this.path = path;
        loadRoot();
    }

    public String getPath() {
        return path;
    }
    
    void loadRoot() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            this.doc = builder.parse(path);
            this.doc.getDocumentElement().normalize();
            this.xPath = XPathFactory.newInstance().newXPath();

        } catch (Exception e) {
            throw new RuntimeException("Could not load XML from " + path, e);
            // e.printStackTrace();
        }
    }

    public Element getRoot() {
        return doc.getDocumentElement();
    }

    public Node node(String expr) {
        try {
            return (Node) xPath.evaluate(expr, doc, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Bad Xpath: " + expr, e);
        }
    }

    public NodeList nodes(String expr) {
        try {
            return (NodeList) xPath.evaluate(expr, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Bad Xpath: " + expr, e);
        }
    }

    public String string(String expr) {
        try {
            return (String) xPath.evaluate(expr, doc, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Bad Xpath: " + expr, e);
        }
    }


    public static void main(String[] args) {
        XMLReader reader = new XMLReader("./renate/scenarios/test-scenario.xml");
        System.out.println("Done!..");
    }
}

