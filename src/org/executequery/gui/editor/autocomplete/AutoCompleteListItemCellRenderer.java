/*
 * AutoCompleteListItemCellRenderer.java
 *
 * Copyright (C) 2002-2017 Takis Diakoumis
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.executequery.gui.editor.autocomplete;

import org.executequery.ApplicationException;
import org.executequery.gui.IconManager;
import org.executequery.log.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.image.ImageObserver;

public class AutoCompleteListItemCellRenderer extends DefaultListCellRenderer {

    private static final int TEXT_ICON_GAP = 10;

    private static final Icon sql92Keyword;
    private static final Icon databaseSpecificKeyword;
    private static final Icon databaseTable;
    private static final Icon nothingFound;
    private static final Icon databaseTableColumn;
    private static final Icon systemFunction;
    private static final Icon databaseFunction;
    private static final Icon databaseProcedure;
    private static final Icon databasePackage;
    private static final Icon variable;
    private static final Icon parameter;
    private static final ImageIcon animatedSpinner;
    private static final Icon databaseTableView;

    static {
        sql92Keyword = IconManager.getIcon("icon_sql92");
        animatedSpinner = (ImageIcon) IconManager.getIcon("icon_loading"/*, "gif"*/);
        databaseSpecificKeyword = IconManager.getIcon("icon_db_keyword");
        nothingFound = IconManager.getIcon("icon_warning");
        databaseTable = IconManager.getIcon("icon_db_table");
        databaseTableColumn = IconManager.getIcon("icon_db_table_column");
        databaseTableView = IconManager.getIcon("icon_db_view");
        systemFunction = IconManager.getIcon("icon_db_function_system");
        databaseFunction = IconManager.getIcon("icon_db_function");
        databaseProcedure = IconManager.getIcon("icon_db_procedure");
        databasePackage = IconManager.getIcon("icon_db_package");
        variable = IconManager.getIcon("icon_variable");
        parameter = IconManager.getIcon("icon_function");
    }


    @Override
    public Component getListCellRendererComponent(JList list, Object value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {

        JLabel listLabel = (JLabel) super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
        listLabel.setIconTextGap(TEXT_ICON_GAP);

        AutoCompleteListItem item = (AutoCompleteListItem) value;

        try {

            switch (item.getType()) {

                case SQL92_KEYWORD:
                    setIcon(sql92Keyword);
                    break;

                case DATABASE_DEFINED_KEYWORD:
                    setIcon(databaseSpecificKeyword);
                    break;

                case DATABASE_TABLE:
                    setIcon(databaseTable);
                    break;

                case DATABASE_VIEW:
                    setIcon(databaseTableView);
                    break;

                case DATABASE_TABLE_COLUMN:
                    setIcon(databaseTableColumn);
                    break;

                case DATABASE_FUNCTION:
                    setIcon(databaseFunction);
                    break;

                case DATABASE_PROCEDURE:
                    setIcon(databaseProcedure);
                    break;
                case DATABASE_PACKAGE:
                    setIcon(databasePackage);
                    break;

                case SYSTEM_FUNCTION:
                    setIcon(systemFunction);
                    break;

                case NOTHING_PROPOSED:
                    setBackground(list.getBackground());
                    setForeground(list.getForeground());
                    setBorder(noFocusBorder);
                    setIcon(nothingFound);
                    break;

                case GENERATING_LIST:
                    setBackground(list.getBackground());
                    setForeground(list.getForeground());
                    setBorder(noFocusBorder);
                    setIcon(animateImageIcon(animatedSpinner, list, index));
                    break;
                case VARIABLE:
                    setIcon(variable);
                    break;
                case PARAMETER:
                    setIcon(parameter);
                    break;
            }


        } catch (Exception e) {

            if (e instanceof NullPointerException) { // setIcon throwing ???

                Log.trace("NPE for item renderer item: " + item.getType().name(), e);
            }

            throw new ApplicationException(e);
        }

        return listLabel;
    }


    private ImageIcon animateImageIcon(ImageIcon icon, final JList list, final int row) {

        icon.setImageObserver(new ImageObserver() {

            public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {

                if (list.isShowing() && (infoflags & (FRAMEBITS | ALLBITS)) != 0) {

                    if (list.getSelectedIndex() == row) {

                        list.repaint();
                    }

                    if (list.isShowing()) {

                        list.repaint(list.getCellBounds(row, row));
                    }

                }
                return (infoflags & (ALLBITS | ABORT)) == 0;
            }

        });

        return icon;
    }

}






