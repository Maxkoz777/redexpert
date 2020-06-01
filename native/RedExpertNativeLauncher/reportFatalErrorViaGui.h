#ifndef REPORT_FATAL_ERROR_VIA_GUI_H_included
#define REPORT_FATAL_ERROR_VIA_GUI_H_included

/**
 * Functions and helpers for displaying errors in graphical mode
 */

static const int NOT_SUPPORTED_ARCH=1;
static const int NOT_SUPPORTED_VERSION=3;
static int dialog_result;
const int CHOOSE_FILE = 3;
const int DOWNLOAD = 4;
const int CANCEL = 5;

#ifdef _WIN32
#define USE_MESSAGE_BOX 1
#include <windows.h>
#include "utils.h"
#else
#define USE_MESSAGE_BOX 0

#include <gtk/gtk.h>
extern "C"  void
ok_button_clicked (GtkButton *button,
            gpointer   data);
static  GtkBuilder *builder;
static GtkRadioButton *rb_download,*rb_file,*rb_cancel;


void gtkMessageBox(const char *title, const char *message)
{
    GtkWidget *dialog;
    GtkWidget *label;
    GtkWidget *content_area;

    gtk_init(NULL, NULL);

    dialog = gtk_dialog_new_with_buttons(title,
                                         NULL,
                                         GTK_DIALOG_MODAL,
                                         GTK_STOCK_OK,
                                         GTK_RESPONSE_ACCEPT,
                                         NULL);
    gtk_container_set_border_width (GTK_CONTAINER(dialog), 5);
    content_area = gtk_dialog_get_content_area(GTK_DIALOG(dialog));
    gtk_container_set_border_width (GTK_CONTAINER(content_area), 15);

    label = gtk_label_new(message);
    gtk_container_add(GTK_CONTAINER(content_area), label);
    gtk_widget_show(label);

    gtk_dialog_run(GTK_DIALOG(dialog));
    gtk_widget_destroy(dialog);
}
char* gtkOpenFile()
{
    GtkWidget *dialog;
    GtkFileChooserAction action = GTK_FILE_CHOOSER_ACTION_SELECT_FOLDER;
    gint res;

    dialog = gtk_file_chooser_dialog_new ("Select java",
                                          NULL,
                                          action,
                                          "_Cancel",
                                          GTK_RESPONSE_CANCEL,
                                          "_Open",
                                          GTK_RESPONSE_ACCEPT,
                                          NULL);

    res = gtk_dialog_run (GTK_DIALOG (dialog));
    char *filename=0;
    if (res == GTK_RESPONSE_ACCEPT)
      {

        GtkFileChooser *chooser = GTK_FILE_CHOOSER (dialog);
        filename = gtk_file_chooser_get_filename (chooser);

      }

    gtk_widget_destroy (dialog);
    return filename;
}
int gtkDialog(std::string path_to_glade,std::string url)
{

    GtkWidget *dialog;
    GError *error = NULL;
    gtk_init(NULL, NULL);
    builder = gtk_builder_new();
    if( ! gtk_builder_add_from_file( builder, path_to_glade.c_str(), &error ) )
        {
            g_warning( "%s", error->message );
            g_error_free (error);
            return( 1 );
        }
dialog = GTK_WIDGET(gtk_builder_get_object(builder, "dialog_jnf"));
    gtk_builder_connect_signals (builder, NULL);
        rb_download=GTK_RADIO_BUTTON(gtk_builder_get_object(builder, "rb_download"));
        rb_file=GTK_RADIO_BUTTON(gtk_builder_get_object(builder, "rb_file"));
        rb_cancel=GTK_RADIO_BUTTON(gtk_builder_get_object(builder, "rb_cancel"));
        GtkTextBuffer * msg = GTK_TEXT_BUFFER(gtk_builder_get_object(builder, "mes_txt"));
        std::string text_msg="Java not found. You can select the path to java manually.\n";
        text_msg.append("You can also start downloading java automatically\n");
        text_msg.append("or download java manually from\n");
        text_msg.append(url);
        gtk_text_buffer_set_text(msg,text_msg.c_str(),text_msg.length());
       // освобождение памяти
       g_object_unref( G_OBJECT( builder ) );

       // Показываем форму и виджеты на ней
       gtk_widget_show(dialog);

       // запуск главного цикла приложения
       gtk_main();
       //system("pause");
       gtk_widget_destroy(dialog);
       return 0;
}
extern "C"  void
ok_button_clicked (GtkButton *button,
            gpointer   data)
{


    if (gtk_toggle_button_get_active (GTK_TOGGLE_BUTTON(rb_download)))
        dialog_result = DOWNLOAD;
    if (gtk_toggle_button_get_active (GTK_TOGGLE_BUTTON(rb_file)))
        dialog_result = CHOOSE_FILE;
    if (gtk_toggle_button_get_active (GTK_TOGGLE_BUTTON(rb_cancel)))
        dialog_result = CANCEL;
    gtk_main_quit();

}

#endif

#include <iostream>
#include <fstream>
#include <sstream>

inline std::string getCurrentDateTime()
{
    time_t now = time(0);
    struct tm  tstruct;
    char  buf[80];
    tstruct = *localtime(&now);
    strftime(buf, sizeof(buf), "%Y-%m-%d %X", &tstruct);
    return std::string(buf);
}
#ifdef WIN32
TCHAR* basicOpenFile()
{
    OPENFILENAME ofn;       // common dialog box structure
    wchar_t szFile[260];       // buffer for file name
    HWND hwnd;              // owner window
    TCHAR* res;              // file handle

    // Initialize OPENFILENAME
    ZeroMemory(&ofn, sizeof(ofn));
    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = GetActiveWindow();
    ofn.lpstrFile = szFile;
    // Set lpstrFile[0] to '\0' so that GetOpenFileName does not
    // use the contents of szFile to initialize itself.
    ofn.lpstrFile[0] = '\0';
    ofn.nMaxFile = sizeof(szFile);
    ofn.lpstrFilter = L"dll\0jvm.dll\0";
    ofn.nFilterIndex = 1;
    ofn.lpstrFileTitle = NULL;
    ofn.nMaxFileTitle = 0;
    ofn.lpstrInitialDir = NULL;
    ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST;

    // Display the Open dialog box.

    if (GetOpenFileName(&ofn)==TRUE)
        res =ofn.lpstrFile;
    else res=0;
    return res;
}



#endif
inline void printErrorToLogFile(const char* log_file, const std::string& app_mess)
{    
    std::ofstream ofs(log_file, std::ofstream::out | std::ofstream::app);
    if(ofs)
    {
        ofs << getCurrentDateTime();
        ofs << std::endl;
        ofs << app_mess;
        ofs << std::endl;
    }
    if(ofs.bad())    //bad() function will check for badbit
    {
        std::cerr << "Writing to log file failed!" << std::endl;
    }
}

inline void reportFatalErrorViaGui(const std::string& programName, const std::string& applicationMessage, std::string supportAddress,int typeError)
{
    const char* log_file = 0;
    std::string path;
#ifdef __linux__
    char* tmp_env = NULL;
    if ((tmp_env = getenv("TMPDIR")) != NULL)
    {
        path.append(tmp_env);
    }
    else
    {
#ifdef P_tmpdir
        path.append(P_tmpdir);
#endif
    }
    if (path.length() == 0)
        path.append("/tmp/");
    path.append("/red_expert.log");
    log_file = path.c_str();
#else
    DWORD dw_ret = 0;
    TCHAR t_path[MAX_PATH];    
    //  Gets the temp path env string (no guarantee it's a valid path).
    dw_ret = GetTempPath(MAX_PATH,          // length of the buffer
                           t_path); // buffer for path
    if (dw_ret > MAX_PATH || (dw_ret == 0))
    {
        std::cerr << "GetTempPath failed";
        log_file = "red_expert.log";
    }
    else
    {
        std::wstring w_path = std::wstring(t_path);
        path = utils::convertUtf16ToUtf8(w_path);
        path.append("\\red_expert.log");
        log_file = path.c_str();
    }

#endif
    if (supportAddress.empty())
    {
        supportAddress = std::string("rdb.support@red-soft.ru");
    }
    std::string arch;
    #if INTPTR_MAX == INT32_MAX
    arch = "x86";
    #elif INTPTR_MAX == INT64_MAX
    arch = "amd64";
    #endif
    std::ostringstream os;
    os << applicationMessage;
    os << std::endl;
    os << "Please copy this message to the clipboard with Ctrl-C and mail it to " << supportAddress << ".";
    os << "You just need to run the application once with the parameter -Djava_home=java_path,";
    os << "where java_path is the path to Java. And the application will remember the path";
    os << std::endl;
    std::string platformMessage(os.str());
    std::string m_mes("Launch error because Java not found.");
    if(typeError==NOT_SUPPORTED_ARCH)
    {
        m_mes.append(" This application need in java with arch: ");
        m_mes.append(arch);
        m_mes.append(".");
    }
    m_mes.append(" Please, check ");
    m_mes.append(log_file);
    m_mes.append(" for error details.");
#if USE_MESSAGE_BOX
    printErrorToLogFile(log_file, platformMessage);
    MessageBox(GetActiveWindow(), utils::convertUtf8ToUtf16(m_mes).c_str(), utils::convertUtf8ToUtf16(programName).c_str(), MB_OK);
#else
    (void)programName;
#endif
    std::cerr << platformMessage;
    printErrorToLogFile(log_file, platformMessage);
#ifdef __linux__
    gtkMessageBox(programName.c_str(), m_mes.c_str());
#endif
}

inline void reportFatalErrorViaGui(const std::string& programName, const std::string& applicationMessage)
{
    reportFatalErrorViaGui(programName, applicationMessage, "",0);
}

#endif
