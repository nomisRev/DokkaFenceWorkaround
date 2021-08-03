package com.github.nomisRev

import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.model.DAnnotation
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.SourceSetDependent
import org.jetbrains.dokka.model.doc.Author
import org.jetbrains.dokka.model.doc.Br
import org.jetbrains.dokka.model.doc.CodeBlock
import org.jetbrains.dokka.model.doc.CodeInline
import org.jetbrains.dokka.model.doc.Constructor
import org.jetbrains.dokka.model.doc.CustomDocTag
import org.jetbrains.dokka.model.doc.CustomTagWrapper
import org.jetbrains.dokka.model.doc.Deprecated
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Param
import org.jetbrains.dokka.model.doc.Property
import org.jetbrains.dokka.model.doc.Receiver
import org.jetbrains.dokka.model.doc.Return
import org.jetbrains.dokka.model.doc.Sample
import org.jetbrains.dokka.model.doc.See
import org.jetbrains.dokka.model.doc.Since
import org.jetbrains.dokka.model.doc.Suppress
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.doc.Throws
import org.jetbrains.dokka.model.doc.Version
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.transformers.documentation.PreMergeDocumentableTransformer

class DokkaFenceWorkaround : DokkaPlugin() {
    val dokkaBasePlugin by lazy { plugin<DokkaBase>() }
    val workaround by extending {
        dokkaBasePlugin.preMergeDocumentableTransformer with WorkAround order { after(dokkaBasePlugin.modulesAndPackagesDocumentation) }
    }
}

object WorkAround : PreMergeDocumentableTransformer {
    override fun invoke(modules: List<DModule>): List<DModule> =
        modules.updateCodeBlocks()
}

private fun List<DModule>.updateCodeBlocks(): List<DModule> =
    map { module ->
        module.copy(packages = module.packages.map { `package` ->
            `package`.copy(
                properties = `package`.properties.map { property ->
                    property.copy(documentation = property.documentation.process())
                },
                functions = `package`.functions.map { function ->
                    function.copy(documentation = function.documentation.process())
                },
                classlikes = `package`.classlikes.map(DClasslike::process),
                typealiases = `package`.typealiases.map { typeAlias ->
                    typeAlias.copy(documentation = typeAlias.documentation.process())
                }
            )
        }
        )
    }

fun SourceSetDependent<DocumentationNode>.process(): SourceSetDependent<DocumentationNode> =
    mapValues { (_, node) -> node.process() }

fun DClasslike.process(): DClasslike =
    when (this) {
        is DClass -> copy(documentation = documentation.process())
        is DEnum -> copy(documentation = documentation.process())
        is DInterface -> copy(documentation = documentation.process())
        is DObject -> copy(documentation = documentation.process())
        is DAnnotation -> copy(documentation = documentation.process())
    }

fun DocumentationNode.process(): DocumentationNode =
    copy(children = children.map {
        when (it) {
            is See -> it.copy(root = it.root.process())
            is Param -> it.copy(root = it.root.process())
            is Throws -> it.copy(root = it.root.process())
            is Sample -> it.copy(root = it.root.process())
            is Property -> it.copy(root = it.root.process())
            is CustomTagWrapper -> it.copy(root = it.root.process())
            is Description -> it.copy(root = it.root.process())
            is Author -> it.copy(root = it.root.process())
            is Version -> it.copy(root = it.root.process())
            is Since -> it.copy(root = it.root.process())
            is Return -> it.copy(root = it.root.process())
            is Receiver -> it.copy(root = it.root.process())
            is Constructor -> it.copy(root = it.root.process())
            is Deprecated -> it.copy(root = it.root.process())
            is Suppress -> it.copy(root = it.root.process())
        }
    })

fun DocTag.process(): DocTag =
    when (this) {
        is CodeBlock -> withFences()
        is CodeInline -> this // Add additional back ticks?
        is CustomDocTag -> copy(children = children.map { it.process() })
        else -> this
    }

fun CodeBlock.withFences(): CodeBlock {
    val fence = params["lang"] ?: ""
    val newChildren = listOf(Text("```$fence")) + Br + children + Br + Text("```")
    return copy(children = newChildren)
}

// Get all codeBlocks => write some dump version
private fun List<DModule>.allCodeBlocks(): List<CodeBlock> =
    flatMap { module ->
        module.packages.flatMap { `package` ->
            `package`.children.flatMap { documentable ->
                documentable.documentation.values.flatMap { node ->
                    node.children.flatMap { tagWrapper ->
                        tagWrapper.children.mapNotNull { docTag ->
                            (docTag as? CodeBlock)
                        }
                    }
                }
            }
        }
    }