from pathlib import Path
p=Path('scripts/apply_social_core.py')
s=p.read_text()
s=s.replace('?.lowercase()? : ""','?.lowercase() ?: ""')
s=s.replace('?.lowercase() ? : ""','?.lowercase() ?: ""')
p.write_text(s)
print('fixed social implementation script syntax')
