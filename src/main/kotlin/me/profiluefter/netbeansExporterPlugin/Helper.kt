package me.profiluefter.netbeansExporterPlugin

import com.intellij.openapi.vfs.VirtualFile
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.StringWriter
import java.util.*
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

val Properties.output: String
    get() {
        val writer = StringWriter()
        store(writer, "Generated by NetBeansExporter-Plugin by Profiluefter")
        return writer.toString()
    }
val Document.content: String
    get() {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }
        val writer = StringWriter()
        transformer.transform(
            DOMSource(this),
            StreamResult(writer)
        )
        return writer.toString()
    }

operator fun Element.set(name: String, value: String) = setAttribute(name, value)
operator fun Element.get(name: String): String = getAttribute(name)
operator fun Node.plusAssign(element: Element) {
    appendChild(element)
}

fun <E> MutableList<E>.addAndReturn(element: E): E {
    this.add(element)
    return element
}

fun VirtualFile.relativePathFrom(root: VirtualFile): String {
    val segments = ArrayList<String>()
    segments.add(this.name)

    var parent: VirtualFile? = this.parent
    while(parent != null) {
        if(parent == root)
            break
        segments.add(parent.name)
        parent = parent.parent
    }

    if(parent == null || parent != root)
        throw NetBeansExportException("Could not find path to source root relative to project root")

    return segments.asReversed().joinToString(File.separator)
}