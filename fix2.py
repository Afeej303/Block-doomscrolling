with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if i == 207:
        new_lines.append(line)
        new_lines.append("                }\n")
    elif i == 239:
        new_lines.append(line)
        new_lines.append("                    }\n")
    elif i == 240:
        pass # replace incorrect } with nothing
    else:
        new_lines.append(line)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.writelines(new_lines)
