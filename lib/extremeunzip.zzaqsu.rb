require 'victoriafresh'
require 'cbor'
require 'lzma'
require 'get_process_mem'

def checkMemoryUsage(lineNumber)
  mem = GetProcessMem.new
  puts("#{lineNumber} ,  Memory: #{mem.mb}") # Debug
end

class ExtremeUnZip
  def initialize
    # 移除 StreamDecoder 初始化，改为直接调用 LZMA.decompress
  end

  def readVfsDataList(wholeCbor, currentBlockFile) 
    compressedVfsDataList = []
    startIndix = wholeCbor['vfsDataListStart']

    puts "list content: #{wholeCbor['vfsDataList']}" # Debug
    
    expected_block_length = nil

    wholeCbor['vfsDataList'].each do |currentBlockInfo|
      length = currentBlockInfo['length']
      
      if expected_block_length.nil?
        expected_block_length = length
      elsif length != expected_block_length
        puts "Warning: unexpected block length detected. Expected: #{expected_block_length}, got: #{length}"
      end
      
      # 直接从文件读取，不依赖 @wholeFileContent
      currentBlockFile.seek(startIndix)
      currentBlock = currentBlockFile.read(length)
      
      compressedVfsDataList << currentBlock
      
      startIndix += length
    end

    # ✅ 关键：释放大对象
    @wholeFileContent = nil
    GC.start

    compressedVfsDataList
  end
  
  def extractVfsDataWithVersionExternalFile(wholeCbor, fileVersion)
    dataFileName = 'victoriafreshdata.w'
    expected_block_length = nil

    if (fileVersion == 14)
        compressedVfsData = wholeCbor['vfsData']
        victoriaFreshData = LZMA.decompress(compressedVfsData)

        File.open(dataFileName, 'wb') do |dataFile|
            dataFile.syswrite(victoriaFreshData)
            puts "After writing initial block, raw data file size: #{File.size(dataFileName)}"
        end
    elsif (fileVersion >= 30)
        compressedVfsDataList = wholeCbor['vfsDataList']
        
        if (fileVersion >= 251)
          compressedVfsDataList = readVfsDataList(wholeCbor, currentBlockFile)
        end 

        puts("data block amount: #{compressedVfsDataList.length}")

        dataBlockCounter = 0
        previous_block_length = nil

        File.open(dataFileName, 'wb') do |dataFile|
compressedVfsDataList.each_with_index do |currentCompressed, index|
  puts("data block counter: #{dataBlockCounter}")
  checkMemoryUsage(34)

  begin
    currentRawData = LZMA.decompress(currentCompressed)
    
    if previous_block_length.nil?
      previous_block_length = currentRawData.length
      expected_block_length ||= previous_block_length
    elsif currentRawData.length != previous_block_length || currentRawData.length == 0
      if currentRawData.length == 0
        puts "Warning: decompressed block length is zero at block #{index}. Generating fake data of expected length."
        # 如果当前解压数据长度为0，则创建一个指定长度的伪造数据块
        currentRawData = "\x00" * expected_block_length
      else
        puts "Warning: unexpected decompressed block length detected at block #{index}. Previous length: #{previous_block_length}, current length: #{currentRawData.length}"
      end
    end
    
    dataFile.syswrite(currentRawData)
    puts "After writing block #{index}, raw data file size: #{File.size(dataFileName)}"
  rescue RuntimeError => e
    puts "Warning: the exz file may be incomplete at block #{index}. Error: #{e.message}"
    
    # 创建一个填充用的假数据块
    fake_data_block = "\x00" * expected_block_length
    dataFile.syswrite(fake_data_block)
    puts "After writing fake block #{index}, raw data file size: #{File.size(dataFileName)}"
    next
  end

  dataBlockCounter += 1
end
        end
    end

    dataFileName
  end

  def exuz(rootPath)
    result = true

    begin
        currentBlockFile = File.new(rootPath, 'rb')
        @wholeFileContent = currentBlockFile.read
        currentBlockFile.close

        checkMemoryUsage(60)

        wholeCborByteArray = @wholeFileContent[4..-1]

        options = {:tolerant => true}
        wholeCbor = CBOR.decode(wholeCborByteArray, options)
            
        fileVersion = wholeCbor['version']
            
        if (fileVersion < 14)
            checkMemoryUsage(85)
            puts 'file version too old'
        else
            compressedVfsMenu = wholeCbor['vfsMenu']
            puts "compressed vfs menu size: #{compressedVfsMenu.size}"

            checkMemoryUsage(90)
            replyByteArray = LZMA.decompress(compressedVfsMenu)
          
            checkMemoryUsage(95)

            victoriaFreshDataFile = extractVfsDataWithVersionExternalFile(wholeCbor, fileVersion)
          
            checkMemoryUsage(100)
            $clipDownloader = VictoriaFresh.new
          
            $clipDownloader.releaseFilesExternalDataFile(replyByteArray, victoriaFreshDataFile)
          
            File.delete(victoriaFreshDataFile)
        end
            
        result = true
    rescue EOFError => e
        puts "Error: the exz file may be incomplete. Error: #{e.message}"
        result = false
    rescue => e
        puts "Unexpected error: #{e.message}"
        result = false
    end
  end
end
