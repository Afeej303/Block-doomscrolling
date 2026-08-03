with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if i == 177:
        new_lines.append('''                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { viewModel.selectedTab.value = 3 },
                        icon = { 
                            Icon(
                                if (viewModel.isConfigurationLocked()) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Security Lock"
                            ) 
                        },
                        label = { Text("Security Lock") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricTeal,
                            selectedTextColor = ElectricTeal,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor = AccentPill
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { viewModel.selectedTab.value = 4 },
                        icon = { Icon(androidx.compose.material.icons.Icons.Default.Build, contentDescription = "Developer") },
                        label = { Text("Developer") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricTeal,
                            selectedTextColor = ElectricTeal,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor = AccentPill
                        )
                    )
''')
        skip = True
    elif skip and '}' in line and 'NavigationBarItem' not in line and 'selectedTab' not in line and 'Icon' not in line and 'colors' not in line and 'label' not in line and 'indicatorColor' not in line:
        pass
    
    if skip and i >= 207:
        skip = False
        
    if not skip and i < 177:
        new_lines.append(line)
        
    if not skip and i >= 208 and i != 235 and i != 236 and i != 237 and i != 238 and i != 239 and i != 240:
        new_lines.append(line)
        
    if i == 235:
        new_lines.append('''                        3 -> LockScreen(
                            viewModel = viewModel,
                            securitySettings = securitySettings
                        )
                        4 -> com.example.ui.DeveloperScreen()
''')
        
with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.writelines(new_lines)
