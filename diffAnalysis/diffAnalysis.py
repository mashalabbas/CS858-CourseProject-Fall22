import pandas as pd
import sys, os


# GLOBAL VARS
## DIRECTORY MACROS
TOP_DIR = "/home/s33khan/Documents/diffAnalysis/"
FILES_DIR = "files/"
INPUT_DIR = "input/"
OUTPUT_DIR = "output/"


def loadInput():
    input_dir = os.path.join(TOP_DIR, FILES_DIR, INPUT_DIR)

    file_to_data = {}

    # Loading data from input files
    for file in os.listdir(input_dir):
        key = file.split(".")[0]
        val = None
        with open(os.path.join(input_dir, file)) as f:
            val = f.read().split("\n")[:-4]
        
        file_to_data[key] = val

    return file_to_data


# Creates data frame from the input data
def createFileDataframes(input_data):
    
    file_to_df = {}
    
    for key, val in input_data.items():
        df = pd.DataFrame(columns=["Class Name", "API Name", "Number of Input Parameter", "Input Parameter Types", "List of Methods Invoked"])

        # Separating classes data
        class_separated = []

        new_class = []
        for line in val:
            new_class.append(line)

            if line == "--------------------------------------------------------":
                add_class_check = False
                for line in new_class:
                    if "Method Invoked:" in line:
                        add_class_check = True
                        break
                if add_class_check:
                    class_separated.append(new_class[:-1])
    
                new_class = []

        add_class_check = False
        for line in new_class:
                    if "Method Invoked:" in line:
                        add_class_check = True
                        break
        if add_class_check:
            class_separated.append(new_class)

        # Extracting information from each class
        for curr_class in class_separated:
            row = {}
            api_lines = []

            class_name = ""
            for line_number in range(len(curr_class)):
                line = curr_class[line_number]
                if "Class: " in line:
                    if(line.split(" ")[1] != "Class:" and line.split(" ")[1] != "SuperClass:"):
                        class_name = line.split(" ")[1]

                elif "Method: " in line:
                    continue

                elif "Instruction: " in line:
                    continue

                elif "Service String: " in line:
                    continue
                
                elif "Binder Class: " in line:
                    continue

                elif "Binder Superclass: " in line:
                    continue

                elif "Sevice Class: " in line:
                    continue

                elif "Sevice Superclass: " in line:
                    continue

                elif "API Name: " in line:
                    api_lines.append(line)
                
                elif "No of Parameters: " in line:
                    api_lines.append(line)

                elif "Parameter Types: " in line:
                    api_lines.append(line)

                elif "Method Invoked: " in line:
                    api_lines.append(line)
                

                if line == "********************":
                    if len(api_lines) != 0:
                        method_invoked_list = []
                        for api_line in api_lines:
                            if "API Name: " in api_line:
                                row["API Name"] = api_line.split(" ")[2]
                            
                            if "No of Parameters: " in api_line:
                                row["Number of Input Parameter"] = api_line.split(" ")[3]

                            if "Parameter Types: " in api_line:
                                row["Input Parameter Types"] = api_line.split(":")[1][1:]
                            
                            if "Method Invoked: " in api_line:
                                method_invoked_list.append(api_line.split(" ")[2])
                        
                        row["List of Methods Invoked"] = str(method_invoked_list)

                        row["Class Name"] = class_name

                        df.loc[len(df.index)] = [row["Class Name"], row["API Name"], row["Number of Input Parameter"], row["Input Parameter Types"], row["List of Methods Invoked"]]

                        # df.concat(row, ignore_index=True)
                        row = {}
                        api_lines = []

        file_to_df[key] = df
        # writeDfToCSV(df, key + "total_data.csv")

    return file_to_df
        

def diffAnalysis(type, input_df):
    if type == "A":
        base_rom = "pixelrom"
        base_rom_df = shrinkDf(input_df[base_rom])

        del input_df[base_rom]

        for key, val in input_df.items():
            curr_df = shrinkDf(val)

            merge_df = base_rom_df.merge(curr_df, on=["Class Name", "API Name", "Number of Input Parameters", "Input Parameter Types"], how="outer", indicator=True)

            writeDfToCSV(merge_df, base_rom + "_" + key + "_mergeAll.csv")


def shrinkDf(df):
    class_name_list = list(map(lambda x : x.split("/")[-1], df["Class Name"].to_list()))

    api_name_list = df["API Name"].to_list()

    no_of_param_list = df["Number of Input Parameter"].to_list()

    type_of_param_list_lsit = df["Input Parameter Types"].to_list()

    df = pd.DataFrame({
        "Class Name" : class_name_list,
        "API Name" : api_name_list,
        "Number of Input Parameters" : no_of_param_list,
        "Input Parameter Types": type_of_param_list_lsit
    })

    return df

def writeDfToCSV(df, filename):
    path = os.path.join(TOP_DIR, FILES_DIR, OUTPUT_DIR, filename)
    df.to_csv(path, index=False)


def main():

    # Get input data
    input_data = loadInput()

    # Create per class dataframe
    input_df = createFileDataframes(input_data)

    # Diff analysis on Class name and API Name
    diffAnalysis("A", input_df)





if __name__ == "__main__":
    try:
        main()
    
    except KeyboardInterrupt:
        print(f'\nUnexpected Error: {KeyboardInterrupt}.\nShutting Down...')
        
        sys.exit(0)