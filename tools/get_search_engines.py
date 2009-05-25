#!/usr/bin/python2.4
# Copyright 2009 Google Inc.
# All Rights Reserved.
"""
Creates the list of search engines for the given locale.

The created list is placed in the res/values-<locale> directory. Also updates
res/values/all_search_engines.xml if required with new data.

Usage: get_search_engines.py <locale>
       get_search_engines.py en-GB

Copyright (c) 2009 Google Inc. All Rights Reserved
"""

__author__ = 'satish@google.com (Satish Sampath)'

import os
import re
import sys
import urllib
from xml.dom import minidom


class SearchEngineManager(object):
  """Manages list of search engines and creates locale specific lists.

  The main method useful for the caller is generateListForLocale(), which
  creates a locale specific search_engines.xml file suitable for use by the
  Android WebSearchProvider implementation.
  """

  def __init__(self):
    """Inits SearchEngineManager with relevant search engine data.

    The search engine data is downloaded from the Chrome source repository.
    """
    self.chrome_data = urllib.urlopen(
        'http://src.chromium.org/viewvc/chrome/trunk/src/chrome/'
        'browser/search_engines/template_url_prepopulate_data.cc').read()
    if self.chrome_data.lower().find('repository not found') != -1:
      print 'Unable to get Chrome source data for search engine list.\nExiting.'
      sys.exit(2)

    self.resdir = os.path.normpath(os.path.join(sys.path[0], '../res'))

  def getXmlString(self, str):
    """Returns an XML-safe string for the given string.

    Given a string from the search engine data structure, convert it to a
    string suitable to write to our XML data file by stripping away NULLs,
    unwanted quotes, wide-string declarations (L"") and replacing C-style
    unicode characters with XML equivalents.
    """
    str = str.strip()
    if str.upper() == 'NULL':
      return ''

    if str.startswith('L"'):
      str = str[2:]

    str = str.strip('"').replace('\\x', '&#x')
    str = str.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    str = str.replace('"', '&quot;').replace('\'', '&apos;')

    return str

  def getEngineData(self, name):
    """Returns an array of strings describing the specified search engine.

    The returned strings are in the same order as in the Chrome source data file
    except that the internal name of the search engine is inserted at the
    beginning of the list.
    """
    # Find the first occurance of this search engine name in the form
    # " <name> =" in the chrome data file.
    re_exp = '\s' + name + '[\s=]'
    search_obj = re.search(re_exp, self.chrome_data)
    if not search_obj:
      print ('Unable to find data for search engine ' + name +
             '. Please check the chrome data file for format changes.')
      return None

    # Extract the struct declaration between the curly braces.
    start_pos = self.chrome_data.find('{', search_obj.start()) + 1;
    end_pos = self.chrome_data.find('};', start_pos);
    engine_data_str = self.chrome_data[start_pos:end_pos]

    # Join multiple line strings into a single string.
    engine_data_str = re.sub('\"\s+\"', '', engine_data_str)
    engine_data_str = re.sub('\"\s+L\"', '', engine_data_str)
    engine_data_str = engine_data_str.replace('"L"', '')

    engine_data = engine_data_str.split(',')
    for i in range(len(engine_data)):
      engine_data[i] = self.getXmlString(engine_data[i])

    # If the last element was an empty string (due to an extra comma at the
    # end), ignore it.
    if not engine_data[len(engine_data) - 1]:
      engine_data.pop()

    # The current last element is the search engine ID which we don't use, so
    # ignore that.
    engine_data.pop()

    engine_data.insert(0, name)

    return engine_data

  def getSearchEnginesForCountry(self, country):
    """Returns the list of search engine names for the given country.

    The data comes from the Chrome data file.
    """
    # The Chrome data file has an array defined with the name 'engines_XX'
    # where XX = country.
    pos = self.chrome_data.find('engines_' + country)
    if pos == -1:
      print ('Unable to find search engine data for country ' + country +
             '. Using default values.')
      country = 'default'
      pos = self.chrome_data.find('engines_' + country)
      if pos == -1:
        print ('Unable to find default search engine data. Please check the'
               ' Chrome data file for format changes.')
        return

    # Extract the text between the curly braces for this array declaration
    engines_start = self.chrome_data.find('{', pos) + 1;
    engines_end = self.chrome_data.find('}', engines_start);
    engines_str = self.chrome_data[engines_start:engines_end]

    # Remove embedded /**/ style comments, white spaces, address-of operators
    # and the trailing comma if any.
    engines_str = re.sub('\/\*.+\*\/', '', engines_str)
    engines_str = re.sub('\s+', '', engines_str)
    engines_str = engines_str.replace('&','')
    engines_str = engines_str.rstrip(',')

    # Split the array into it's elements and ignore the 'google' entry since we
    # don't need it in the this search engine list.
    engines = engines_str.split(',')
    try:
      engines.remove('google')
    except ValueError:
      pass

    return engines

  def addNewSearchEnginesToXml(self, engines):
    """Adds new search engines to the all_search_engines.xml file.
    """
    file = open(os.path.join(self.resdir, 'values/all_search_engines.xml'))
    all_engines = file.read()
    file.close()

    for engine_name in engines:
      engine_data = self.getEngineData(engine_name)

      # The known search engines are listed as xml nodes with the names as
      # attributes in double quotes, so search for this search engine name in
      # double quotes to check if it is already listed.
      if all_engines.find('"' + engine_data[0] + '"') == -1:
        print engine_data[1] + " added to all_search_engines.xml"

        # Add a new listing containing the internal name and all other fields
        # except the display label (which goes into the locale-specific
        # search_engines.xml file).
        lines = ['  <string-array name="%s">\n' % (engine_data[0])]
        for i in range(2, len(engine_data)):
          lines.append('    <item>%s</item>\n' % (engine_data[i]))
        lines.append('  </string-array>\n')

        # Add this new entry to the XML file at the end.
        pos = all_engines.rfind('\n', 0, -2) + 1
        all_engines = all_engines[0:pos] + ''.join(lines) + all_engines[pos:]

    # Make sure what we have created is valid XML :) No need to check for errors
    # as the script will terminate with an exception if the XML was malformed.
    engines_dom = minidom.parseString(all_engines)

    file = open(os.path.join(self.resdir, 'values/all_search_engines.xml'), 'w')
    file.write(all_engines)
    file.close()

  def generateListForLocale(self, locale):
    """Creates a new locale specific search_engines.xml file.

    The new file contains search engines specific to that country. If required
    this function updates all_search_engines.xml file with any new search
    engine data necessary.
    """
    separator_pos = locale.find('-')
    if separator_pos == -1:
      print ('Locale must be of format <language>-<country>. For e.g.'
             ' "es-US" or "en-GB"')
      return

    language = locale[0:separator_pos]
    country = locale[separator_pos + 1:].upper()
    dir_path = os.path.join(self.resdir, 'values-' + language + '-r' + country)
    if os.path.exists(dir_path) and not os.path.isdir(dir_path):
      print "File exists in output directory path " + dir_path + ". Please remove it and try again."
      return

    engines = self.getSearchEnginesForCountry(country)
    self.addNewSearchEnginesToXml(engines)

    # Create the locale specific search_engines.xml file. Each
    # search_engines.xml file has a hardcoded list of 7 items. If there are less
    # than 7 search engines for this country, the remaining items are marked as
    # enabled=false.
    text = []
    for i in range(1, 8):
      flag = 'false'
      name = ''
      label = ''
      if i <= len(engines):
        engine_data = self.getEngineData(engines[i - 1])
        flag = 'true'
        name = engine_data[0]
        label = engine_data[1]

      text.append('  <bool   name="engine_%d_enabled">%s</bool>\n' % (i, flag))
      text.append('  <string name="engine_%d_name">%s</string>\n' % (i, name))
      text.append('  <string name="engine_%d_label">%s</string>\n' % (i, label))

    # Load the template file and insert the new contents before the last line.
    template_text = open(
        os.path.join(sys.path[0], 'search_engines.template.xml')).read()
    pos = template_text.rfind('\n', 0, -2) + 1
    contents = template_text[0:pos] + ''.join(text) + template_text[pos:]

    # Make sure what we have created is valid XML :) No need to check for errors
    # as the script will terminate with an exception if the XML was malformed.
    engines_dom = minidom.parseString(contents)

    if not os.path.exists(dir_path):
      os.makedirs(dir_path)
      print 'Created directory ' + dir_path
    file_path = os.path.join(dir_path, 'search_engines.xml')
    file = open(file_path, 'w')
    file.write(contents)
    file.close()
    print 'Resource file ' + file_path + ' updated.'


if __name__ == "__main__":
  if len(sys.argv) < 2:
    print __doc__
    sys.exit(2)

  SearchEngineManager().generateListForLocale(sys.argv[1])
