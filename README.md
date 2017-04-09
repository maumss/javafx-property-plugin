# JavaFx Property Plugin

Creates JavaFx property bean methods plugin for `NetBeans IDE 8.2+`.

## Feature

This plugin will generate methods using naming conventions that form the JavaFX property pattern.

## Install

Go to `Tools > Plugins > Downloaded Intalled > Add Plugins...` and add the `javafxpropertyplugin.nbm` file.

## Usage

Just create the atributes, e.g. "private IntegerProperty id;", key `ALT+INS`, and choose the option "JavaFX Property Pattern". This plugin will create the class constructors, the methods "public final Integer getId()", "public final void setId(Integer id)" and "public IntegerProperty idProperty()". 

- Creates class constructors
- Creates public methods

## Credits
Mauricio Soares da Silva - [maumss@users.noreply.github.com](mailto:maumss@users.noreply.github.com)

## License

[GNU General Public License (GPL) v3](http://www.gnu.org/licenses/)

Copyright &copy; 2015 Mauricio Soares da Silva

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.

