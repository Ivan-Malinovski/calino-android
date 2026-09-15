# Run after installing the debug APK on the API 36 emulator.
# Uses only the emulator; never selects a physical ADB device.
import subprocess, xml.etree.ElementTree as ET, re
ADB = ['adb','-s','emulator-5554']
def adb(*args):
    return subprocess.check_output(ADB + list(args))
def dump():
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/window.xml'))
def heading():
    return next(n.get('text') for n in dump().iter('node') if n.get('text','').startswith('Week '))
def tap_desc(fragment):
    n=next(n for n in dump().iter('node') if fragment in n.get('content-desc',''))
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
adb('shell','am','force-stop','calino.malinov.ski.nativeDebug')
adb('shell','am','start','-W','-n','calino.malinov.ski.nativeDebug/.MainActivity')
assert heading() == 'Week 21 · Mon 18 May'
adb('shell','input','swipe','900','400','150','400','1000')
assert heading() == 'Week 22 · Mon 25 May'
adb('shell','input','swipe','150','400','900','400','120')
assert heading() == 'Week 21 · Mon 18 May', heading()
adb('shell','input','swipe','540','400','610','400','900')
assert heading() == 'Week 21 · Mon 18 May', heading()
tap_desc('Change calendar zoom')
for day, weekday in [(27,'Wed'),(5,'Tue'),(22,'Fri'),(1,'Fri'),(18,'Mon'),(31,'Sun'),(12,'Tue')]:
    tap_desc(f'May {day},')
    actual=heading()
    assert f'{weekday} {day} May' in actual, actual
    print(actual, flush=True)
adb('shell','screencap','-p','/sdcard/calino-month-fix.png')
adb('pull','/sdcard/calino-month-fix.png','/tmp/calino-month-fix.png')
print('PASS: slow forward, fast reverse, short cancellation, seven month date selections', flush=True)
