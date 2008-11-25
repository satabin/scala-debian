/* NSC -- new Scala compiler
 * Copyright 2007-2008 LAMP/EPFL
 * @author Lex Spoon
 */
// $Id: Plugins.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc.plugins

import java.io.File

/** Support for run-time loading of compiler plugins.
 *
 *  @author Lex Spoon
 *  @version 1.0, 2007-5-21
 */
trait Plugins { self: Global =>

  /** Load a rough list of the plugins.  For speed, it
   *  does not instantiate a compiler run.  Therefore it cannot
   *  test for same-named phases or other problems that are
   *  filtered from the final list of plugins. */
  protected def loadRoughPluginsList(): List[Plugin] = {
    val jars = settings.plugin.value.map(new File(_))
    val dirs =
      for (name <- settings.pluginsDir.value.split(File.pathSeparator).toList)
	yield new File(name)

    for (plugClass <- Plugin.loadAllFrom(jars, dirs, settings.disable.value))
    yield Plugin.instantiate(plugClass, this)
  }

  private var roughPluginsListCache: Option[List[Plugin]] = None

  protected def roughPluginsList: List[Plugin] =
    roughPluginsListCache match {
      case Some(list) => list
      case None =>
	roughPluginsListCache = Some(loadRoughPluginsList)
        roughPluginsListCache.get
    }


  /** Load all available plugins.  Skips plugins that
   *  either have the same name as another one, or which
   *  define a phase name that another one does.
   */
  protected def loadPlugins(): List[Plugin] = {
    // remove any with conflicting names or subcomponent names
    def pick(
      plugins: List[Plugin], 
      plugNames: Set[String], 
      phaseNames: Set[String]): List[Plugin] =
    {
      plugins match {
	case Nil => Nil
	case plug :: rest =>
	  val plugPhaseNames = Set.empty ++ plug.components.map(_.phaseName)
	  def withoutPlug = pick(rest, plugNames, plugPhaseNames)
	  def withPlug =
	    (plug :: 
	     pick(rest, 
		  plugNames+plug.name,
		  phaseNames++plugPhaseNames))

	  if (plugNames.contains(plug.name)) {
	    if (settings.verbose.value)
	      inform("[skipping a repeated plugin: " + plug.name + "]")
	    withoutPlug
	  } else if (settings.disable.value contains(plug.name)) {
	    if (settings.verbose.value)
	      inform("[disabling plugin: " + plug.name + "]")
	    withoutPlug
	  } else {
	    val commonPhases = phaseNames.intersect(plugPhaseNames)
	    if (!commonPhases.isEmpty) {
	      if (settings.verbose.value)
		inform("[skipping plugin " + plug.name +
		       "because it repeats phase names: " +
		       commonPhases.mkString(", ") + "]")
	      withoutPlug
	    } else {
	      if (settings.verbose.value)
		inform("[loaded plugin " + plug.name + "]")
	      withPlug
	    }
	  }
      }
    }

    val plugs =
    pick(roughPluginsList,
	 Set.empty,
	 Set.empty ++ builtInPhaseDescriptors.map(_.phaseName))

    for (req <- settings.require.value; if !plugs.exists(p => p.name==req))
      error("Missing required plugin: " + req)

    
    for (plug <- plugs) {
      val nameColon = plug.name + ":"
      val opts = for {
	raw <- settings.pluginOptions.value
	if raw.startsWith(nameColon)
      } yield raw.substring(nameColon.length)

      if (!opts.isEmpty)
	plug.processOptions(opts, error)
    }

    for {
      opt <- settings.pluginOptions.value
      if !plugs.exists(p => opt.startsWith(p.name + ":"))
    } error("bad option: -P:" + opt)

    plugs
  }


  private var pluginsCache: Option[List[Plugin]] = None

  def plugins: List[Plugin] = {
    if (pluginsCache.isEmpty)
      pluginsCache = Some(loadPlugins)
    pluginsCache.get
  }


  /** A description of all the plugins that are loaded */
  def pluginDescriptions: String = {
    val messages =
      for (plugin <- roughPluginsList)
	yield plugin.name + " - " + plugin.description
    messages.mkString("\n")
  }


  /** Compute a full list of phase descriptors, including
   *  both built-in phases and those coming from plugins. */
  protected def computePhaseDescriptors: List[SubComponent] = {
    def insert(descs: List[SubComponent], component: PluginComponent)
    :List[SubComponent] =
    {
      descs match {
	case Nil => assert(false); Nil
        case hd::rest if "parser" == component.runsAfter =>
          component :: hd :: rest
	case hd::rest if hd.phaseName == component.runsAfter =>
	  hd :: component :: rest
	case hd :: rest =>
	  hd :: (insert(rest, component))
      }
    }

    var descriptors = builtInPhaseDescriptors
    var plugsLeft = plugins.flatMap(_.components)

    // Insert all the plugins, one by one.  Note that
    // plugins are allowed to depend on each other, thus
    // complicating the algorithm.	  

    while (!plugsLeft.isEmpty) {
      val nextPlug = plugsLeft.find(plug =>
        plug.runsAfter == "parser" || 
        descriptors.exists(d => d.phaseName == plug.runsAfter))
      nextPlug match {
	case None =>
	  error("Failed to load some plugin phases:")
	  for (plug <- plugsLeft)
	    error (plug.phaseName + " depends on " + plug.runsAfter)
	  return descriptors
	case Some(nextPlug) =>
	  descriptors = insert(descriptors, nextPlug)
          plugsLeft = plugsLeft.filter(p => !(p eq nextPlug))
      }
    }

    descriptors
  }

  /** Summary of the options for all loaded plugins */
  def pluginOptionsHelp: String = {
    val buf = new StringBuffer
    for (plug <- roughPluginsList; help <- plug.optionsHelp) {
      buf append ("Options for plugin " + plug.name + ":\n")
      buf append help
      buf append "\n"
    }
    buf.toString
  }
}
